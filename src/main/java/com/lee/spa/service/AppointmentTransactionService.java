package com.lee.spa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.dto.CreateAppointmentRequest;
import com.lee.spa.entity.Appointment;
import com.lee.spa.entity.AppointmentSlot;
import com.lee.spa.entity.SpaService;
import com.lee.spa.mapper.AppointmentMapper;
import com.lee.spa.mapper.AppointmentSlotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor

// 专门承担“创建预约时需要事务保护的数据库操作”
public class AppointmentTransactionService {

    private final AppointmentMapper appointmentMapper;

    private final AppointmentSlotMapper appointmentSlotMapper;

    /*
        因为接下来它需要：
                        1. 查 appointment_slot
                        2. 写 appointment
                        3. 写 appointment_slot
     */


    /*
        为什么不直接只传 request？

                    因为前面的锁外部分已经做过：

                    查询 SpaService
                            ↓
                    取得 durationMinutes
                            ↓
                    取得 price
                            ↓
                    计算 endTime

                    没有必要事务开始以后再重新做一遍这些不会因为预约竞争而变化的工作。

                    所以可以把前面已经得到的数据直接交给事务方法。 ⬇
     */
    @Transactional
    public Appointment creatAppointmentInTransaction(CreateAppointmentRequest request,
                                                     SpaService spaService,
                                                     LocalTime endTime,
                                                     LocalDateTime startDateTime,
                                                     LocalDateTime endDateTime){
        Appointment existing =
                getByRequestId(request.getRequestId());

        if (existing != null) {
            return existing;
        }

        Appointment appointment = null;



        //        第七步：写slot冲突查询
        LambdaQueryWrapper<AppointmentSlot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppointmentSlot::getStaffId,request.getStaffId())
                .ge(AppointmentSlot::getSlotTime,startDateTime)
                .lt(AppointmentSlot::getSlotTime,endDateTime);
        List<AppointmentSlot> conflictSlots = appointmentSlotMapper.selectList(wrapper);

//        判断是否冲突
        if(!conflictSlots.isEmpty()){
            throw new BusinessException("该时间段已被预约，请重新选择");
        }

        //        第八步：构造appointment
        appointment = new Appointment();
        appointment.setUserId(request.getUserId());  //来自前端
        appointment.setServiceId(request.getServiceId());  //来自前端
        appointment.setStaffId(request.getStaffId());  //来自前端
        appointment.setAppointmentDate(request.getAppointmentDate());  //来自前端
        appointment.setStartTime(request.getStartTime());
        appointment.setEndTime(endTime);
        appointment.setAmount(spaService.getPrice());  // 来自数据库
        appointment.setRemark(request.getRemark());   //

        appointment.setStatus(1);  // 之后改成枚举，现在暂时直接设置

        appointment.setRequestId(request.getRequestId());

        /*
        System.currentTimeMillis()
            获取当前系统时间戳：从 1970-01-01 00:00:00 UTC 到现在的毫秒数，返回 long 类型。
            示例：1756789012345
            "APT" + 时间戳
            字符串拼接，把前缀 APT 和毫秒数字拼成一整个字符串。
         */
        String appointmentNo = "APT" + System.currentTimeMillis();  //后面我们可以再优化成更规范的雪花 ID 或业务单号，但现在先跑通。
        appointment.setAppointmentNo(appointmentNo);

        //        第九步：insert appointment
        /*
        前面构造的：
                Appointment appointment
                目前只是 Java 内存里的对象。
                数据库里还没有。
         */
        appointmentMapper.insert(appointment);

//        // 临时事务测试代码
//        boolean rollbackTest = true;
//        if (rollbackTest) {
//            throw new RuntimeException(
//                    "事务回滚测试：模拟 appointment 写入后发生异常"
//            );
//        }

        /*
        插入完成之后：
                appointment.getId()
                就可以拿到数据库生成的 ID。
                例如：
                appointment.getId() = 15
                这个 ID 马上拿来关联 appointment_slot。
         */


//        第十步：生成预约slot
        LocalDateTime slotTime = startDateTime;



        while(slotTime.isBefore(endDateTime)){

            //        第十一步：创建AppointmentSlot
            AppointmentSlot slot = new AppointmentSlot();
            slot.setAppointmentId(appointment.getId());  // 设置预约 ID
            slot.setStaffId(request.getStaffId());
            slot.setSlotTime(slotTime);

            appointmentSlotMapper.insert(slot);




            // 创建一个slot
            slotTime = slotTime.plusMinutes(30);
        }


        return appointment;
    }

    private Appointment getByRequestId(String requestId) {

        LambdaQueryWrapper<Appointment> wrapper =
                new LambdaQueryWrapper<>();

        wrapper.eq(
                Appointment::getRequestId,
                requestId
        );

        return appointmentMapper.selectOne(wrapper);
    }
}
