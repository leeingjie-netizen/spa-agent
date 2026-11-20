package com.lee.spa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.entity.Appointment;
import com.lee.spa.entity.StaffSchedule;
import com.lee.spa.entity.StaffWeeklyRule;
import com.lee.spa.mapper.AppointmentMapper;
import com.lee.spa.mapper.StaffScheduleMapper;
import com.lee.spa.vo.ScheduleGenerationResult.Entry;
import com.lee.spa.vo.ScheduleGenerationResult.Outcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Separate bean ensures failures roll back only the current day. */
@Service
@RequiredArgsConstructor
public class ScheduleGenerationWriter {
    private final StaffScheduleMapper schedules;
    private final AppointmentMapper appointments;

    public boolean scheduleExists(Long staffId, LocalDate date) {
        return schedules.selectCount(new LambdaQueryWrapper<StaffSchedule>()
                .eq(StaffSchedule::getStaffId, staffId).eq(StaffSchedule::getWorkDate, date)) > 0;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Entry insertMissing(Long staffId, LocalDate date, StaffWeeklyRule rule) {
        StaffSchedule existing = schedules.selectOne(new LambdaQueryWrapper<StaffSchedule>()
                .eq(StaffSchedule::getStaffId, staffId).eq(StaffSchedule::getWorkDate, date));
        if (existing != null) {
            return entry(staffId, date, Outcome.EXISTING, "保留已有排班", List.of());
        }
        validate(rule);
        StaffSchedule schedule = new StaffSchedule();
        schedule.setStaffId(staffId);
        schedule.setWorkDate(date);
        schedule.setStatus(rule.getIsWorking());
        schedule.setSource("AUTO");
        schedule.setStartTime(rule.getStartTime());
        schedule.setEndTime(rule.getEndTime());
        schedule.setBreakStartTime(rule.getBreakStartTime());
        schedule.setBreakEndTime(rule.getBreakEndTime());

        // Unknown legacy statuses are also protected; only explicitly cancelled bookings are ignored.
        List<Long> conflicts = appointments.selectList(new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getStaffId, staffId).eq(Appointment::getAppointmentDate, date)
                        .ne(Appointment::getStatus, 3)).stream()
                .filter(appointment -> !compatible(schedule, appointment))
                .map(Appointment::getId).toList();
        if (!conflicts.isEmpty()) {
            return entry(staffId, date, Outcome.CONFLICT, "规则与已有预约冲突，未生成排班", conflicts);
        }
        // Database uk_staff_date is the final protection for concurrent generators/manual inserts.
        schedules.insert(schedule);
        return entry(staffId, date, Outcome.GENERATED, "已生成", List.of());
    }

    private boolean compatible(StaffSchedule schedule, Appointment appointment) {
        if (appointment.getStartTime() == null || appointment.getEndTime() == null) return false;
        long duration = Duration.between(appointment.getStartTime(), appointment.getEndTime()).toMinutes();
        return duration > 0 && duration <= Integer.MAX_VALUE
                && ScheduleAvailabilityPolicy.isAvailable(schedule,
                appointment.getAppointmentDate().atTime(appointment.getStartTime()), (int) duration);
    }

    private void validate(StaffWeeklyRule rule) {
        Integer working = rule.getIsWorking();
        if (working == null || (working != 0 && working != 1)) invalid();
        LocalTime start = rule.getStartTime(), end = rule.getEndTime();
        LocalTime breakStart = rule.getBreakStartTime(), breakEnd = rule.getBreakEndTime();
        if (working == 0) {
            if (start != null || end != null || breakStart != null || breakEnd != null) invalid();
            return;
        }
        if (start == null || end == null || !start.isBefore(end)) invalid();
        if ((breakStart == null) != (breakEnd == null)) invalid();
        if (breakStart != null && (!breakStart.isBefore(breakEnd)
                || breakStart.isBefore(start) || breakEnd.isAfter(end))) invalid();
        for (LocalTime time : new LocalTime[]{start, end, breakStart, breakEnd}) {
            if (time != null && (time.getMinute() % 30 != 0 || time.getSecond() != 0 || time.getNano() != 0)) invalid();
        }
    }

    private void invalid() { throw new BusinessException("周规则时间非法，未生成排班"); }

    private Entry entry(Long staffId, LocalDate date, Outcome outcome, String message, List<Long> conflicts) {
        return new Entry(staffId, date, outcome, message, conflicts);
    }
}
