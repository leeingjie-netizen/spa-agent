package com.lee.spa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lee.spa.entity.*;
import com.lee.spa.mapper.*;
import com.lee.spa.service.SpaServiceService;
import com.lee.spa.service.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffServiceImpl implements StaffService {

    /**
     * 这里你一定要建立这个感觉：
     * 查 staff
     * → StaffMapper
     *
     * 查 staff_service
     * → StaffServiceRelationMapper
     *
     * 查 spa_service
     * → SpaServiceMapper
     */
    private final StaffMapper staffMapper;

    private final StaffServiceRelationMapper staffServiceRelationMapper;

    private final SpaServiceMapper spaServiceMapper;

    private final StaffScheduleMapper staffScheduleMapper;

    private final AppointmentSlotMapper appointmentSlotMapper;

    private final SpaServiceService spaServiceService;

    /**
     * 获取员工列表
     */
    @Override
    public List<Staff> getStaffList() {
        LambdaQueryWrapper<Staff> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Staff::getStatus,1);
        return staffMapper.selectList(wrapper);
    }

    /**
     * 根据 id 获取员工信息
     * @param id
     * @return
     */
    @Override
    public Staff getStaffById(Long id) {
        return staffMapper.selectById(id);
    }


    /**
     * 根据 id 获取员工服务列表
     *
     * 注入两个 Mapper。
     * 为什么两个？
     * 因为你要查两张表：
     *      staff_service
     *      spa_service
     *
     *    为什么方法写在 StaffServiceImpl？
     *    因为这是：
     *          技师相关的业务。不是单纯查某一张表。
     */
    @Override
    public List<SpaService> getServicesById(Long staffId) {
        // 翻译成人话：
        //     我要创建一个针对 StaffServiceRelation 的查询条件。
        LambdaQueryWrapper<StaffServiceRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StaffServiceRelation::getStaffId,staffId);
        List<StaffServiceRelation> relations = staffServiceRelationMapper.selectList(wrapper);
        /**
         * 上面这三行你连起来看：
         * 等价于：
         *      SELECT *
         *      FROM staff_service
         *      WHERE staff_id = ?;
         * 如果 staffId = 1，你得到：
         *      relations = [
         *     {staffId=1, serviceId=1},
         *     {staffId=1, serviceId=3}
         * ]
         *
         * 现在你手里不是服务，而是关系：
         *      {staffId=1, serviceId=1}
         *      {staffId=1, serviceId=3}
         * 你真正需要的是：
         * 1
         * 3
         *
         * 所以接下来：提取 serviceId
         */
        List<Long> serviceIds = relations.stream()
                .map(StaffServiceRelation::getServiceId)
                .toList();
        /**
         * 这段你暂时不用死磕 Stream。
         * 先把它读成人话：
         * 遍历 relations
         *      ↓
         * 每一个 relation
         *      ↓
         * 只取它的 serviceId
         *      ↓
         * 组成新的 List
         *
         *
         * 所以：
         * List<StaffServiceRelation>
         * 变成：
         * List<Long>
         *
         */

        // 接下来处理空数据：
        // 这里要考虑：如果这个技师什么都不会呢？relations可能为空
        if (relations.isEmpty()) {
            return Collections.emptyList();  //意思是：没有服务关联，就返回空列表。
        }

        //现在已经拿到：List<Long> serviceIds
        // 接下来去 spa_service 查真正服务
        return spaServiceMapper.selectBatchIds(serviceIds);
        /**
         * 这句基本等价于：
         * SELECT * FROM spa_service WHERE id IN (1, 3);
         * 最终得到：List<SpaService>
         *
         *
         */
    }

    /**
     * 根据 id 和日期获取员工排班信息
      * @param staffId
     * @param date
     * @return
     */
    @Override
    public StaffSchedule getStaffSchedule(Long staffId, LocalDate date) {
        LambdaQueryWrapper<StaffSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StaffSchedule::getStaffId,staffId)
                .eq(StaffSchedule::getWorkDate,date)
                .eq(StaffSchedule::getStatus,1);
        return staffScheduleMapper.selectOne(wrapper);
    }

    @Override
    public List<LocalTime> getAvailableSlots(Long staffId, LocalDate date, Long serviceId) {

        SpaService spaService = spaServiceMapper.selectById(serviceId);  // 查询有没有该服务
        if(spaService == null) {
            return Collections.emptyList();
        }

        boolean supported = supportsService(staffId, serviceId);   // 查询该技师是否提供该服务

        if(!supported) {
            return Collections.emptyList();
        }

        /*
            查询该技师在该日已占用的slots
            在appointmentSlot表中查询
            根据员工id(staffId)查询预约时间段
            获得预约时间段列表
         */
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime nextDayStart = date.plusDays(1).atStartOfDay();

        LambdaQueryWrapper<AppointmentSlot> slotWrapper = new LambdaQueryWrapper<>(); //  在appointmentSlot表中查询
        //        根据员工id(staffId)查询当日0点到次日0点之间的预约时间段
        slotWrapper.eq(AppointmentSlot::getStaffId,staffId)
                .ge(AppointmentSlot::getSlotTime,dayStart)
                .lt(AppointmentSlot::getSlotTime,nextDayStart);
//        获得预约时间段列表
        List<AppointmentSlot> occupiedSlots = appointmentSlotMapper.selectList(slotWrapper);
        // 得到的数据大概是：[
        // AppointmentSlot(slotTime=2026-09-07T15:00),
        // AppointmentSlot(slotTime=2026-09-07T15:30),
        // AppointmentSlot(slotTime=2026-09-07T16:00)
        //]

        // 但是我们接下来每次判断：这个时间被占了吗？
        //  如果每一次都遍历整个 List，不太方便。
        //  所以我们把它变成：Set<LocalDateTime>     很适合快速判断“这个东西存不存在”。

//        把 AppointmentSlot 转成时间 Set
        Set<LocalDateTime> occupiedTimes =
                occupiedSlots.stream()
                        .map(AppointmentSlot::getSlotTime)
                        .collect(Collectors.toSet());

        // 翻译成人话：
        //
        //  occupiedSlots
        //      ↓
        //  遍历每一个 AppointmentSlot
        //      ↓
        //  只拿它的 slotTime
        //      ↓
        //  组成 Set
        //
        //  原来：
        //
        //  AppointmentSlot
        //  AppointmentSlot
        //  AppointmentSlot
        //  AppointmentSlot
        //
        //  变成：
        //
        //  2026-09-07 15:00
        //  2026-09-07 15:30
        //  2026-09-07 16:00   得到今天这个技师所有已经被占用的30分钟时间片
        //  查询当天排班
        StaffSchedule staffSchedule = getStaffSchedule(staffId, date);
        if (staffSchedule == null) {

            return Collections.emptyList();     // 当天不工作
        }

        ArrayList<LocalTime> availableSlots = new ArrayList<>();


        LocalTime current = staffSchedule.getStartTime();  // 从员工上班时间开始算当前时间
        Integer durationMinutes = spaService.getDurationMinutes();  // 服务时长


        while(!current.plusMinutes(durationMinutes).isAfter(staffSchedule.getEndTime())){
            if(!hasConflict(date, current, durationMinutes, occupiedTimes)){
                // 如果从上班时间开始，每加30分钟都和占用时间不冲突，就把当前时间加到结果列表中
                availableSlots.add(current);
            }
            current = current.plusMinutes(30);
        }

        return availableSlots;
    }

    /**
     * 判断给定时间是否有冲突
     * @param date
     * @param startTime
     * @param durationMinutes
     * @param occupiedTimes
     * @return
     */
    private boolean hasConflict(
            LocalDate date,
            LocalTime startTime,
            Integer durationMinutes,
            Set<LocalDateTime> occupiedTimes) {
        LocalTime currentSlot = startTime;   // 预约服务的开始时间
        LocalTime endTime = startTime.plusMinutes(durationMinutes);  //服务结束时间

        while(currentSlot.isBefore(endTime)){
            LocalDateTime slotDateTime = LocalDateTime.of(date, currentSlot);
            if (occupiedTimes.contains(slotDateTime)) {
                return true;
            }

            currentSlot = currentSlot.plusMinutes(30);
        }

        return false;
    }



    public boolean supportsService(Long staffId, Long serviceId) {
        return staffServiceRelationMapper.selectCount(
                new LambdaQueryWrapper<StaffServiceRelation>()
                        .eq(StaffServiceRelation::getStaffId, staffId)
                        .eq(StaffServiceRelation::getServiceId, serviceId)
        ) > 0;
    }

    /**
     * 根据服务id获取提供该服务的员工列表
     * @param serviceId
     * @return
     */
    @Override
    public List<Staff> getStaffByService(Long serviceId) {

        LambdaQueryWrapper<StaffServiceRelation> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(StaffServiceRelation::getServiceId,serviceId);
        List<StaffServiceRelation> relations = staffServiceRelationMapper.selectList(wrapper);
        List<Long> staffIds = relations.stream().map(StaffServiceRelation::getStaffId).toList();

        if(relations.isEmpty()){

            return Collections.emptyList();

        }

        List<Staff> staffs = staffMapper.selectBatchIds(staffIds);
        return staffs;
    }

}


