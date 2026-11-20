package com.lee.spa.service;

import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.entity.StaffSchedule;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/** Shared half-open interval policy used by availability and booking. */
public final class ScheduleAvailabilityPolicy {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private ScheduleAvailabilityPolicy() { }

    public static boolean isAvailable(StaffSchedule schedule, LocalDateTime start, int duration) {
        if (schedule == null || !Integer.valueOf(1).equals(schedule.getStatus())
                || start == null || duration <= 0 || schedule.getWorkDate() == null
                || !schedule.getWorkDate().equals(start.toLocalDate())
                || schedule.getStartTime() == null || schedule.getEndTime() == null
                || !schedule.getStartTime().isBefore(schedule.getEndTime())) return false;
        LocalDateTime end = start.plusMinutes(duration);
        if (!end.toLocalDate().equals(start.toLocalDate())
                || start.toLocalTime().isBefore(schedule.getStartTime())
                || end.toLocalTime().isAfter(schedule.getEndTime())) return false;
        LocalTime breakStart = schedule.getBreakStartTime();
        LocalTime breakEnd = schedule.getBreakEndTime();
        if ((breakStart == null) != (breakEnd == null)) return false;
        if (breakStart == null) return true;
        if (!breakStart.isBefore(breakEnd) || breakStart.isBefore(schedule.getStartTime())
                || breakEnd.isAfter(schedule.getEndTime())) return false;
        return !start.toLocalTime().isBefore(breakEnd) || !end.toLocalTime().isAfter(breakStart);
    }

    public static void requireAvailable(StaffSchedule schedule, LocalDateTime start, int duration) {
        if (!isAvailable(schedule, start, duration)) {
            throw new BusinessException("预约时间不在有效排班内或与午休冲突");
        }
    }
}
