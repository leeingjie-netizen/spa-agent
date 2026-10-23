package com.lee.spa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.dto.CreateAppointmentRequest;
import com.lee.spa.entity.*;
import com.lee.spa.mapper.AppointmentMapper;
import com.lee.spa.mapper.AppointmentSlotMapper;
import com.lee.spa.mapper.SpaServiceMapper;
import com.lee.spa.mapper.StaffMapper;
import com.lee.spa.service.AppointmentService;
import com.lee.spa.service.AppointmentTransactionService;
import com.lee.spa.service.SpaServiceService;
import com.lee.spa.service.StaffService;
import com.lee.spa.vo.AppointmentSummaryVO;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Service
@RequiredArgsConstructor
/**
 * Service 负责回答“业务应该怎么做”。
 * 总指挥：
 * 负责：查服务
 * → 检查技师
 * → 检查排班
 * → 算时间
 * → 检查冲突
 * → 写 appointment
 * → 写 slot
 */
public class AppointmentServiceImpl implements AppointmentService {
    private final SpaServiceService spaServiceService;
    private final StaffService staffService;
    private final AppointmentSlotMapper appointmentSlotMapper;
    private final AppointmentMapper appointmentMapper;

    private final RedissonClient redissonClient;

    private final AppointmentTransactionService appointmentTransactionService;
    private final SpaServiceMapper spaServiceMapper;
    private final StaffMapper staffMapper;

//    第十三步：加事务

    @Override
    public Appointment createAppointment(CreateAppointmentRequest request) {

//        第一次幂等检查放在哪里？放在 createAppointment() 比较靠前的位置。
//        requestId 基本校验
//                  ↓
//        查询已有 appointment
//                  ↓
//        如果存在直接返回
//                  ↓
//        后面的普通预约校验
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {

            throw new BusinessException("预约请求标识不能为空");
        }

        // ② 幂等快速检查
        Appointment existing = getByRequestId(request.getRequestId());

        if (existing != null) {

            return existing;

        }

//        第一步：校验预约开始时间
        /*
        校验开始时间是不是 30 分钟粒度
        我们规定合法时间只能是：
                                12:00:00
                                12:30:00
                                13:00:00
                                13:30:00
                       不允许：
                                12:10:00
                                12:15:00
                                12:30:10
         */
        LocalTime startTime = request.getStartTime();

        if(startTime.getMinute() !=0 && startTime.getMinute() != 30){
            throw new BusinessException("预约时间必须为30分钟时间片");
        }

        // 检查秒数
        if (startTime.getSecond() != 0) {
            throw new BusinessException("预约时间秒数必须为0");
        }

        Staff staffById = staffService.getStaffById(request.getStaffId());
        if(staffById == null){
            throw new BusinessException("技师不存在");
        }

//        第二步：查询服务
        SpaService spaService = spaServiceService.getServiceById(request.getServiceId());
        if (spaService == null){
            throw new BusinessException("服务不存在");
        }

//        第三步：校验技师是否支持该服务
        boolean supportsService = staffService.supportsService(request.getStaffId(), request.getServiceId());
        if (!supportsService){
            throw new BusinessException("技师不支持该服务");
        }

//        第四步：查询排班 + 计算服务结束时间
        /*
            拿到具体一条排班对象，从里面继续拿开始时间、结束时间
         */
        StaffSchedule staffSchedule = staffService.getStaffSchedule(request.getStaffId(), request.getAppointmentDate());
        if (staffSchedule == null){
            throw new BusinessException("该技师当日没有排班");
        }

        Integer durationMinutes = spaService.getDurationMinutes();
        LocalTime endTime = startTime.plusMinutes(durationMinutes);


//        第五步：判断开始和结束时间是否属于技师上班时间
//                    先拿到技师的上班、下班时间
        LocalTime workStartTime = staffSchedule.getStartTime();
        LocalTime workEndTime = staffSchedule.getEndTime();
        if (startTime.isBefore(workStartTime) || endTime.isAfter(workEndTime)){
            throw new BusinessException("预约时间不在技师排班范围内");
        }
        //        第六步：判断预约时间slot是否有冲突
//        先把 LocalDate 和 LocalTime 合成 LocalDateTime
        LocalDate appointmentDate = request.getAppointmentDate();
        LocalDateTime startDateTime = LocalDateTime.of(appointmentDate, startTime);
        LocalDateTime endDateTime  = LocalDateTime.of(appointmentDate,endTime);

        //    第十四步：（补充）创建预约也应该拒绝过去时间
        if(startDateTime.isBefore(LocalDateTime.now())){
            throw new BusinessException("不能预约过去的时间");
        }
//=====================================================================================================
        /*
        在这里加锁，防止并发创建预约时的冲突，使用 Redisson 实现分布式锁
         */
        String lockKey = "spa:appointment:lock:"
                        + request.getStaffId()
                        + ":"
                        + request.getAppointmentDate();
        RLock lock = redissonClient.getLock(lockKey);  //getLock()并不是已经获得锁。它只是：根据这个 Key 拿到一个 Redisson 锁对象。此时还没有真正去竞争 Redis 中的锁。

        // 准备 locked
        boolean locked = false;  // 后面tryLock可能成功、可能失败

        Appointment appointment = null;

        try{

            System.out.println(Thread.currentThread().getName() + "开始尝试获取锁：" + lockKey);
            locked = lock.tryLock(
                    3, //最多等3秒
                    10, //拿到锁后自动存在10秒
                    TimeUnit.SECONDS
            );
            // 如果没抢到锁
            if(!locked){
                throw new BusinessException("当前预约人数较多，请稍后重新选择时间");
            }

            System.out.println(
                    Thread.currentThread().getName()
                            + " 成功获得锁：" + lockKey
            );

            /*****************************************************************
             * 这里放真正的预约关键区
             * 查询 appointment_slot 冲突
             *          ↓
             * insert appointment
             *          ↓
             * insert appointment_slot
             ***************************************************************/
            return appointmentTransactionService.creatAppointmentInTransaction(
                    request,
                    spaService,
                    endTime,
                    startDateTime,
                    endDateTime
            );




        } catch (InterruptedException e) {

            // throw new RuntimeException(e);
            Thread.currentThread().interrupt(); //恢复中断标记
            throw new BusinessException("预约创建失败，请稍后重试(获取锁失败)");

        } finally {

            if(locked && lock.isHeldByCurrentThread()){

                System.out.println(Thread.currentThread().getName() + "准备释放锁 " + lockKey);

                lock.unlock();
            }

        }
        //        第十二步：返回Appointment



    }

    /**
     * 根据用户ID查询预约信息
     * @param userId
     * @return
     */
    @Override
    public List<Appointment> getAppointmentsByUserId(Long userId) {
        LambdaQueryWrapper<Appointment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Appointment::getUserId, userId)
                .orderByDesc(Appointment::getAppointmentDate)
                .orderByDesc(Appointment::getStartTime);

        return appointmentMapper.selectList(wrapper);
    }

    @Override
    public List<AppointmentSummaryVO> getAppointmentSummariesByUserId(Long userId) {
        // 1.  这里查userId = 当前用户，得到多个Appointment
        List<Appointment> appointments = appointmentMapper.selectList(
                new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getUserId, userId)
                .orderByDesc(Appointment::getAppointmentDate,
                        Appointment::getStartTime)
                );
        // 2. 然后逐条转换为VO
        List<AppointmentSummaryVO> result = new ArrayList<>();

        for (Appointment appointment : appointments){

            AppointmentSummaryVO vo = new AppointmentSummaryVO();

            vo.setAppointmentId(
                    appointment.getId()
            );

            vo.setAppointmentNo(
                    appointment.getAppointmentNo()
            );

            vo.setServiceId(
                    appointment.getServiceId()
            );

            vo.setStaffId(
                    appointment.getStaffId()
            );

            vo.setAppointmentDate(
                    appointment.getAppointmentDate()
            );

            vo.setStartTime(
                    appointment.getStartTime()
            );

            vo.setEndTime(
                    appointment.getEndTime()
            );

            vo.setAmount(
                    appointment.getAmount()
            );

            vo.setStatus(
                    appointment.getStatus()
            );

            // ⬆ 到这里其实只是把 Appointment 的属性复制到 AppointmentSummaryVO 中

            // 3. 然后补serviceName. appointment表中只有serviceId,但AI/用户想看到的是：“肩颈护理”
            SpaService spaService = spaServiceMapper.selectById(appointment.getServiceId());
            if (spaService != null){
                vo.setServiceName(spaService.getName());
            }

            Staff staff = staffMapper.selectById(appointment.getStaffId());
            if(staff != null){
                vo.setStaffName(staff.getName());
            }

            String cancelReason = getCancelUnavailableReason(appointment);
            if(cancelReason == null){  // 可以取消
                vo.setCancellable(true);
                vo.setCancelReason(null);
            }else{
                // 不可取消
                vo.setCancellable(false);
                vo.setCancelReason(cancelReason);
            }

            result.add(vo);

        }

        return result;
    }


    /**
     * 根据预约ID取消预约
     * @param appointmentId
     * @return
     */
    @Transactional
    /*
    假设：
            appointment status 更新成功
            但删除 Slot 时数据库异常。
            如果没有事务：
            appointment = 已取消

            appointment_slot = 还在
            这就出现数据不一致。
            所以事务保证：
            UPDATE appointment
                    +
            DELETE appointment_slot
            要么一起成功，要么一起失败。
     */
    @Override
    public Boolean cancelAppointmentByAptId(Long appointmentId, Long userId) {

        Appointment appointment = appointmentMapper.selectById(appointmentId);

        if(appointment == null){
            throw new BusinessException("预约不存在");
        }

        if(!appointment.getUserId().equals(userId)){
            throw new BusinessException("无权限取消他人预约");
        }

//        判断状态
        /*
        不过这里有一个 Java 小细节。
                因为：getStatus()
                返回的是：Integer
                更稳妥的写法是：
                if (Integer.valueOf(3)
                        .equals(appointment.getStatus())) {
                    throw new RuntimeException("该预约已经取消");
                }
                为什么不用：
                appointment.getStatus() == 3
                虽然很多时候也能正常工作，但 Integer 是包装类型。
                以后你要逐渐养成：
                Integer.valueOf(3).equals(...)
                这种习惯，避免包装类型比较带来的坑。
         */
//        if(Integer.valueOf(3).equals(appointment.getStatus())){
//            throw new BusinessException("预约已取消");
//        }
////        已完成的预约不能取消
//        if(Integer.valueOf(2).equals(appointment.getStatus())){
//            throw new BusinessException("已完成的预约不能取消");
//        }
//
////        已经过了预约开始时间，不能取消
//        LocalDateTime appointmentStart = LocalDateTime.of(appointment.getAppointmentDate(),appointment.getStartTime());
//        if(LocalDateTime.now().isAfter(appointmentStart)){
//            throw new BusinessException("预约开始时间已过，不能取消");
//        }

        String cancelReason = getCancelUnavailableReason(appointment);
        if(cancelReason != null){   // 说明不能取消
            throw new BusinessException(cancelReason);
        }

        /*
        这样现在允许取消的就是：0 待确认     1 已预约
         */
        appointment.setStatus(3);
        appointmentMapper.updateById(appointment);  //updateById() 的意思就是：根据这个对象的 id 更新数据库。
        LambdaQueryWrapper<AppointmentSlot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppointmentSlot::getAppointmentId,appointmentId);   //对应 SQL：where appointment_id = ?
        appointmentSlotMapper.delete(wrapper);

//        下面写的应该是错的
//        List<AppointmentSlot> slots = appointmentSlotMapper.selectList(wrapper);
//        for (AppointmentSlot slot : slots) {
//            appointmentSlotMapper.deleteById(slot.getId());
//        }
        return true;
    }

    /**
     * 根据 requestId 查询预约信息
     * @param requestId
     * @return
     */
    private Appointment getByRequestId(String requestId) {

        LambdaQueryWrapper<Appointment> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(Appointment::getRequestId,requestId);

        return appointmentMapper.selectOne(wrapper);


    }

    private String getCancelUnavailableReason(
            Appointment appointment) {

        if (appointment.getStatus() == 3) {
            return "预约已经取消";
        }

        if (appointment.getStatus() == 2) {
            return "预约已经完成";
        }

        LocalDateTime appointmentStartTime =
                LocalDateTime.of(
                        appointment.getAppointmentDate(),
                        appointment.getStartTime()
                );

        if (!appointmentStartTime.isAfter(
                LocalDateTime.now())) {

            return "预约开始时间已过，无法取消";
        }

        return null;
    }
}
