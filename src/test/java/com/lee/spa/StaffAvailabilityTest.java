package com.lee.spa;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lee.spa.entity.*;
import com.lee.spa.mapper.*;
import com.lee.spa.service.SpaServiceService;
import com.lee.spa.service.impl.StaffServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class StaffAvailabilityTest {
    @Mock com.lee.spa.service.ScheduleConstraintService constraints;
    @Mock StaffMapper staffs;
    @Mock StaffServiceRelationMapper relations;
    @Mock SpaServiceMapper services;
    @Mock StaffScheduleMapper schedules;
    @Mock AppointmentSlotMapper slots;
    @Mock SpaServiceService serviceService;
    @Mock StaffWeeklyRuleMapper weeklyRules;
    @InjectMocks StaffServiceImpl subject;
    LocalDate date = LocalDate.of(2035,1,1);
    StaffSchedule schedule;
    @BeforeEach void setup() {
        lenient().when(constraints.isAvailable(anyLong(),any(),anyInt())).thenReturn(true);
        Staff staff = new Staff(); staff.setStatus(1); when(staffs.selectById(1L)).thenReturn(staff);
        SpaService service = new SpaService(); service.setStatus(1); service.setDurationMinutes(45);
        when(services.selectById(2L)).thenReturn(service);
        when(relations.selectCount(any(Wrapper.class))).thenReturn(1L);
        schedule = new StaffSchedule(); schedule.setStatus(1); schedule.setWorkDate(date);
        schedule.setStartTime(LocalTime.of(9,0)); schedule.setEndTime(LocalTime.of(18,0));
        schedule.setBreakStartTime(LocalTime.NOON); schedule.setBreakEndTime(LocalTime.of(13,0));
        when(schedules.selectOne(any(Wrapper.class))).thenReturn(schedule);
    }
    @Test void availableStartsExcludeFullServiceOverlapWithLunchAndOccupiedPartialBucket() {
        AppointmentSlot occupied = new AppointmentSlot(); occupied.setSlotTime(date.atTime(10,30));
        when(slots.selectList(any(Wrapper.class))).thenReturn(List.of(occupied));
        List<LocalTime> available = subject.getAvailableSlots(1L,date,2L);
        assertFalse(available.contains(LocalTime.of(10,0)));
        assertFalse(available.contains(LocalTime.of(10,30)));
        assertFalse(available.contains(LocalTime.of(11,30)));
        assertFalse(available.contains(LocalTime.of(12,0)));
        assertTrue(available.contains(LocalTime.of(11,0)));
        assertTrue(available.contains(LocalTime.of(13,0)));
        assertFalse(available.contains(LocalTime.of(17,30)));
    }
    @Test void offGridLegacyShiftOnlyExposesHalfHourStarts() {
        schedule.setStartTime(LocalTime.of(9,15));
        when(slots.selectList(any(Wrapper.class))).thenReturn(List.of());
        List<LocalTime> available = subject.getAvailableSlots(1L,date,2L);
        assertEquals(LocalTime.of(9,30),available.get(0));
        assertTrue(available.stream().allMatch(t -> t.getMinute() % 30 == 0));
    }
}
