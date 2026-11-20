package com.lee.spa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lee.spa.common.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import com.lee.spa.service.ScheduleAvailabilityPolicy;
import com.lee.spa.dto.SaveWeeklyRuleRequest;
import com.lee.spa.entity.*;
import com.lee.spa.mapper.*;
import com.lee.spa.service.SpaServiceService;
import com.lee.spa.service.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final StaffWeeklyRuleMapper staffWeeklyRuleMapper;

    private final com.lee.spa.service.ScheduleConstraintService scheduleConstraints;

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
        if(spaService == null || !Integer.valueOf(1).equals(spaService.getStatus())
                || spaService.getDurationMinutes() == null || spaService.getDurationMinutes() <= 0) {
            return Collections.emptyList();
        }

        boolean supported = supportsService(staffId, serviceId);   // 查询该技师是否提供该服务

        Staff staff = staffMapper.selectById(staffId);
        if(!supported || staff == null || !Integer.valueOf(1).equals(staff.getStatus())) {
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


        if (staffSchedule.getStartTime() == null || staffSchedule.getEndTime() == null) return availableSlots;
        LocalDateTime current = date.atTime(staffSchedule.getStartTime());
        // Legacy shifts may start off-grid; only expose valid booking start times.
        int remainder = current.getMinute() % 30;
        if (remainder != 0 || current.getSecond() != 0 || current.getNano() != 0) {
            current = current.withSecond(0).withNano(0).plusMinutes(30 - remainder);
        }
        int durationMinutes = spaService.getDurationMinutes();
        LocalDateTime now = LocalDateTime.now(ScheduleAvailabilityPolicy.BUSINESS_ZONE);
        while (current.isBefore(date.atTime(staffSchedule.getEndTime()))) {
            if (current.isAfter(now)
                    && ScheduleAvailabilityPolicy.isAvailable(staffSchedule, current, durationMinutes)
                    && scheduleConstraints.isAvailable(staffId, current, durationMinutes)
                    && !hasConflict(date, current.toLocalTime(), durationMinutes, occupiedTimes)) {
                availableSlots.add(current.toLocalTime());
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
        LocalDateTime start = date.atTime(startTime);
        LocalDateTime end = start.plusMinutes(durationMinutes);
        return occupiedTimes.stream().anyMatch(slot -> slot.isBefore(end)
                && slot.plusMinutes(30).isAfter(start));
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
        return staffs.stream().filter(s -> Integer.valueOf(1).equals(s.getStatus())).toList();
    }

    @Override
    public List<StaffWeeklyRule> getWeeklyRulesByStaffId(Long staffId) {

        LambdaQueryWrapper<StaffWeeklyRule> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(StaffWeeklyRule::getStaffId,staffId)
                .orderByAsc(StaffWeeklyRule::getDayOfWeek);


        return staffWeeklyRuleMapper.selectList(wrapper);
    }

    private void validateWeeklyRule(
            SaveWeeklyRuleRequest request) {

        Integer working = request.getIsWorking();

        if (working == null || (working != 0 && working != 1)) {
            throw new BusinessException("是否上班只能为0或1");
        }

        LocalTime start = request.getStartTime();
        LocalTime end = request.getEndTime();
        LocalTime breakStart = request.getBreakStartTime();
        LocalTime breakEnd = request.getBreakEndTime();

        // 休息日：不能传入工作时间或午休时间
        if (working == 0) {
            if (start != null || end != null
                    || breakStart != null || breakEnd != null) {
                throw new BusinessException("休息日不能设置工作时间");
            }
            return;
        }

        // 工作日：上下班时间不能为空
        if (start == null || end == null) {
            throw new BusinessException("工作日必须设置上下班时间");
        }

        if (!start.isBefore(end)) {
            throw new BusinessException("上班时间必须早于下班时间");
        }

        // 午休必须成对填写
        if ((breakStart == null) != (breakEnd == null)) {
            throw new BusinessException("午休开始和结束时间必须同时填写");
        }

        if (breakStart != null) {
            if (!breakStart.isBefore(breakEnd)
                    || breakStart.isBefore(start)
                    || breakEnd.isAfter(end)) {
                throw new BusinessException("午休时间必须位于工作时间内");
            }
        }

        // 与当前预约系统的30分钟刻度保持一致
        for (LocalTime time :
                new LocalTime[]{start, end, breakStart, breakEnd}) {

            if (time != null && !isHalfHourTime(time)) {
                throw new BusinessException("时间必须为整点或半点");
            }
        }
    }

    private boolean isHalfHourTime(LocalTime time) {
        return (time.getMinute() == 0 || time.getMinute() == 30)
                && time.getSecond() == 0
                && time.getNano() == 0;
    }

    @Override
    @Transactional
    public StaffWeeklyRule saveOrUpdateWeeklyRule(Long staffId, Integer dayOfWeek, SaveWeeklyRuleRequest request) {
        // 假设 Lisa 原本有午休，后来管理员取消午休，如果直接使用：staffWeeklyRuleMapper.updateById(existing);
        // MyBatis-Plus 默认通常不会把值为 null 的字段包含在更新 SQL 中。
        //这就可能导致：管理员想删除午休，结果数据库仍然保留原来的午休时间。
        //因此本节的更新操作使用 LambdaUpdateWrapper，明确要求数据库把需要清空的时间写成 NULL。

        // 1. 校验技师和星期
        if (staffId == null || staffId <= 0
                || staffMapper.selectById(staffId) == null) {
            throw new BusinessException("技师不存在");
        }

        if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7) {
            throw new BusinessException("星期必须为1到7");
        }

        // 2. 校验工作时间
        validateWeeklyRule(request);

        // 3. 查找该技师、该星期的现有规则
        LambdaQueryWrapper<StaffWeeklyRule> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(StaffWeeklyRule::getStaffId, staffId)
                .eq(StaffWeeklyRule::getDayOfWeek, dayOfWeek);

        StaffWeeklyRule existing = staffWeeklyRuleMapper.selectOne(wrapper);

        // 4. 没有记录：新增
        if (existing == null) {

            StaffWeeklyRule rule = new StaffWeeklyRule();

            rule.setStaffId(staffId);
            rule.setDayOfWeek(dayOfWeek);
            rule.setIsWorking(request.getIsWorking());
            rule.setStartTime(request.getStartTime());
            rule.setEndTime(request.getEndTime());
            rule.setBreakStartTime(request.getBreakStartTime());
            rule.setBreakEndTime(request.getBreakEndTime());

            try {
                staffWeeklyRuleMapper.insert(rule);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(
                        "该星期规则已被其他操作创建，请刷新后重试");
            }

            return staffWeeklyRuleMapper.selectById(rule.getId());
        }

        // 5. 已有记录：更新
        LambdaUpdateWrapper<StaffWeeklyRule> update = new LambdaUpdateWrapper<>();

        update.eq(StaffWeeklyRule::getId, existing.getId())
                .set(StaffWeeklyRule::getIsWorking,
                        request.getIsWorking())
                .set(StaffWeeklyRule::getStartTime,
                        request.getStartTime())
                .set(StaffWeeklyRule::getEndTime,
                        request.getEndTime())
                .set(StaffWeeklyRule::getBreakStartTime,
                        request.getBreakStartTime())
                .set(StaffWeeklyRule::getBreakEndTime,
                        request.getBreakEndTime());

        int rows = staffWeeklyRuleMapper.update(null, update);

        if (rows != 1) {
            throw new BusinessException("排班规则更新失败");
        }

        // 6. 重新查询，返回数据库中的最终结果
        return staffWeeklyRuleMapper.selectById(existing.getId());
    }

}


