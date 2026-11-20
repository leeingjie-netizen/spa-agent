package com.lee.spa.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lee.spa.entity.Appointment;
import com.lee.spa.entity.StaffSchedule;
import com.lee.spa.entity.StaffWeeklyRule;
import com.lee.spa.mapper.AppointmentMapper;
import com.lee.spa.mapper.StaffScheduleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.lee.spa.vo.ScheduleGenerationResult.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduleGenerationWriterTest {
    private final StaffScheduleMapper schedules = mock(StaffScheduleMapper.class);
    private final AppointmentMapper appointments = mock(AppointmentMapper.class);
    private final ScheduleGenerationWriter writer = new ScheduleGenerationWriter(schedules, appointments);
    private final LocalDate date = LocalDate.of(2026, 11, 16);

    @BeforeEach
    void setup() {
        when(appointments.selectList(any(Wrapper.class))).thenReturn(List.of());
    }

    @Test
    void preservesManualAutomaticAndLegacySchedules() {
        for (String source : new String[]{"MANUAL", "AUTO", null}) {
            StaffSchedule existing = new StaffSchedule(); existing.setSource(source);
            when(schedules.selectOne(any(Wrapper.class))).thenReturn(existing);
            assertEquals(EXISTING, writer.insertMissing(1L, date, working()).outcome());
        }
        verify(schedules, never()).insert(any(StaffSchedule.class));
        verifyNoInteractions(appointments);
    }

    @Test
    void writesExplicitRestAndWorkingDaysIncludingLunch() {
        StaffWeeklyRule rest = new StaffWeeklyRule(); rest.setIsWorking(0);
        assertEquals(GENERATED, writer.insertMissing(1L, date, rest).outcome());
        assertEquals(GENERATED, writer.insertMissing(1L, date.plusDays(1), working()).outcome());
        ArgumentCaptor<StaffSchedule> captured = ArgumentCaptor.forClass(StaffSchedule.class);
        verify(schedules, times(2)).insert(captured.capture());
        assertEquals(0, captured.getAllValues().get(0).getStatus());
        assertNull(captured.getAllValues().get(0).getStartTime());
        StaffSchedule work = captured.getAllValues().get(1);
        assertEquals("AUTO", work.getSource());
        assertEquals(LocalTime.NOON, work.getBreakStartTime());
        assertEquals(1, work.getStatus());
    }

    @Test
    void restOutsideHoursAndLunchConflictsNeverWrite() {
        Appointment appointment = new Appointment(); appointment.setId(88L);
        appointment.setAppointmentDate(date); appointment.setStartTime(LocalTime.of(11, 30));
        appointment.setEndTime(LocalTime.of(12, 15));
        when(appointments.selectList(any(Wrapper.class))).thenReturn(List.of(appointment));
        assertEquals(List.of(88L), writer.insertMissing(1L, date, working()).conflictingAppointmentIds());
        StaffWeeklyRule rest = new StaffWeeklyRule(); rest.setIsWorking(0);
        assertEquals(CONFLICT, writer.insertMissing(1L, date, rest).outcome());
        appointment.setStartTime(LocalTime.of(8, 30)); appointment.setEndTime(LocalTime.of(9, 15));
        assertEquals(CONFLICT, writer.insertMissing(1L, date, working()).outcome());
        verify(schedules, never()).insert(any(StaffSchedule.class));
    }

    @Test
    void compatibleFortyFiveMinuteBookingAllowsGeneration() {
        Appointment appointment = new Appointment(); appointment.setId(99L);
        appointment.setAppointmentDate(date); appointment.setStartTime(LocalTime.of(9, 30));
        appointment.setEndTime(LocalTime.of(10, 15));
        when(appointments.selectList(any(Wrapper.class))).thenReturn(List.of(appointment));
        assertEquals(GENERATED, writer.insertMissing(1L, date, working()).outcome());
    }

    @Test
    void invalidLegacyRulesAreRejectedWithoutWrites() {
        StaffWeeklyRule rule = working(); rule.setBreakEndTime(null);
        assertThrows(RuntimeException.class, () -> writer.insertMissing(1L, date, rule));
        StaffWeeklyRule invalid = working(); invalid.setEndTime(LocalTime.of(8, 0));
        assertThrows(RuntimeException.class, () -> writer.insertMissing(1L, date, invalid));
        verify(schedules, never()).insert(any(StaffSchedule.class));
    }

    @Test
    void eachDayHasAnIndependentTransaction() throws Exception {
        Transactional annotation = ScheduleGenerationWriter.class
                .getMethod("insertMissing", Long.class, LocalDate.class, StaffWeeklyRule.class)
                .getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW, annotation.propagation());
    }

    private StaffWeeklyRule working() {
        StaffWeeklyRule rule = new StaffWeeklyRule(); rule.setIsWorking(1);
        rule.setStartTime(LocalTime.of(9, 0)); rule.setEndTime(LocalTime.of(18, 0));
        rule.setBreakStartTime(LocalTime.NOON); rule.setBreakEndTime(LocalTime.of(13, 0));
        return rule;
    }
}
