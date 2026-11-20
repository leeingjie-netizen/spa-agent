package com.lee.spa;

import com.lee.spa.entity.StaffSchedule;
import com.lee.spa.service.ScheduleAvailabilityPolicy;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleAvailabilityPolicyTest {
    private final LocalDate date = LocalDate.of(2030, 1, 1);
    private StaffSchedule schedule() {
        StaffSchedule s = new StaffSchedule();
        s.setWorkDate(date); s.setStatus(1);
        s.setStartTime(LocalTime.of(9,0)); s.setEndTime(LocalTime.of(22,0));
        s.setBreakStartTime(LocalTime.NOON); s.setBreakEndTime(LocalTime.of(13,0));
        return s;
    }
    @Test void lunchUsesHalfOpenBoundariesAndFullDuration() {
        StaffSchedule s = schedule();
        assertTrue(ScheduleAvailabilityPolicy.isAvailable(s, date.atTime(11,0), 60));
        assertFalse(ScheduleAvailabilityPolicy.isAvailable(s, date.atTime(11,30), 45));
        assertFalse(ScheduleAvailabilityPolicy.isAvailable(s, date.atTime(12,30), 30));
        assertTrue(ScheduleAvailabilityPolicy.isAvailable(s, date.atTime(13,0), 90));
    }
    @Test void rejectsRestMalformedBreakAndCrossMidnight() {
        StaffSchedule s = schedule(); s.setStatus(0);
        assertFalse(ScheduleAvailabilityPolicy.isAvailable(s, date.atTime(10,0), 30));
        s.setStatus(1); s.setBreakEndTime(null);
        assertFalse(ScheduleAvailabilityPolicy.isAvailable(s, date.atTime(10,0), 30));
        s = schedule();
        assertFalse(ScheduleAvailabilityPolicy.isAvailable(s, date.atTime(21,30), 180));
        assertFalse(ScheduleAvailabilityPolicy.isAvailable(s, date.atTime(9,0), 0));
    }
}
