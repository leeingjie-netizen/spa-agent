package com.lee.spa;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.dto.CreateAppointmentRequest;
import com.lee.spa.entity.*;
import com.lee.spa.mapper.*;
import com.lee.spa.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentTransactionServiceTest {
    @Mock com.lee.spa.commerce.OrderService orders;
    @Mock ScheduleConstraintService constraints;
    @Mock AppointmentMapper appointments;
    @Mock AppointmentSlotMapper slots;
    @Mock StaffScheduleMapper schedules;
    @Mock SpaServiceMapper services;
    @Mock StaffMapper staffMapper;
    @Mock StaffService staffService;
    @InjectMocks AppointmentTransactionService subject;
    CreateAppointmentRequest request;
    StaffSchedule schedule;
    SpaService service;
    @BeforeEach void setup() {
        request = new CreateAppointmentRequest();
        request.setUserId(1L); request.setStaffId(2L); request.setServiceId(3L);
        request.setRequestId("test-request"); request.setAppointmentDate(LocalDate.of(2035,1,1));
        request.setStartTime(LocalTime.of(10,0));
        schedule = new StaffSchedule(); schedule.setStatus(1); schedule.setWorkDate(request.getAppointmentDate());
        schedule.setStartTime(LocalTime.of(9,0)); schedule.setEndTime(LocalTime.of(18,0));
        schedule.setBreakStartTime(LocalTime.NOON); schedule.setBreakEndTime(LocalTime.of(13,0));
        service = new SpaService(); service.setStatus(1); service.setPrice(new BigDecimal("99.00")); service.setDurationMinutes(45);
    }
    void arrangeCurrentState() {
        when(schedules.selectForUpdate(2L, request.getAppointmentDate())).thenReturn(schedule);
        when(services.selectById(3L)).thenReturn(service);
        Staff staff = new Staff(); staff.setStatus(1); when(staffMapper.selectById(2L)).thenReturn(staff);
        when(staffService.supportsService(2L,3L)).thenReturn(true);
    }
    Appointment book() { return subject.creatAppointmentInTransaction(request, service, LocalTime.NOON, null, null); }
    @Test void createsTwoBucketsFor45MinutesAndUsesCurrentPriceAndDuration() {
        arrangeCurrentState(); when(slots.selectList(any(Wrapper.class))).thenReturn(List.of());
        doAnswer(call -> { ((Appointment)call.getArgument(0)).setId(123L); return 1; }).when(appointments).insert(any(Appointment.class));
        Appointment result = book();
        assertEquals(LocalTime.of(10,45),result.getEndTime());
        assertEquals(service.getPrice(),result.getAmount());
        ArgumentCaptor<AppointmentSlot> captor = ArgumentCaptor.forClass(AppointmentSlot.class);
        verify(slots,times(2)).insert(captor.capture());
        assertEquals(List.of(request.getAppointmentDate().atTime(10,0),request.getAppointmentDate().atTime(10,30)),
                captor.getAllValues().stream().map(AppointmentSlot::getSlotTime).toList());
    }
    @Test void transactionRechecksLatestLunchBeforeWriting() {
        arrangeCurrentState(); request.setStartTime(LocalTime.of(11,30));
        assertThrows(BusinessException.class,this::book);
        verify(appointments,never()).insert(any(Appointment.class)); verifyNoInteractions(slots);
    }
    @Test void transactionRechecksRestBeforeWriting() {
        arrangeCurrentState(); schedule.setStatus(0);
        assertThrows(BusinessException.class,this::book);
        verify(appointments,never()).insert(any(Appointment.class));
    }
    @Test void occupiedBucketRejectsBeforeAnyInsert() {
        arrangeCurrentState(); when(slots.selectList(any(Wrapper.class))).thenReturn(List.of(new AppointmentSlot()));
        assertThrows(BusinessException.class,this::book);
        verify(appointments,never()).insert(any(Appointment.class));
    }
    @Test void requestIdCannotExposeAnotherUsersAppointment() {
        Appointment existing = new Appointment(); existing.setUserId(999L);
        when(appointments.selectOne(any(Wrapper.class))).thenReturn(existing);
        assertThrows(BusinessException.class,this::book);
        verifyNoInteractions(schedules,services,slots);
    }
}
