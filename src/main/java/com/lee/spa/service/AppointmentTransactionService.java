package com.lee.spa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.dto.CreateAppointmentRequest;
import com.lee.spa.entity.*;
import com.lee.spa.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppointmentTransactionService {
    private final com.lee.spa.commerce.OrderService orders;
    private final AppointmentMapper appointmentMapper;
    private final AppointmentSlotMapper appointmentSlotMapper;
    private final StaffScheduleMapper staffScheduleMapper;
    private final SpaServiceMapper spaServiceMapper;
    private final StaffMapper staffMapper;
    private final StaffService staffService;
    private final ScheduleConstraintService scheduleConstraints;

    /** The caller retains the Redisson watchdog lock until this transaction commits. */
    @Transactional
    public Appointment creatAppointmentInTransaction(CreateAppointmentRequest request,
            SpaService ignoredService, LocalTime ignoredEndTime,
            LocalDateTime ignoredStart, LocalDateTime ignoredEnd) {
        Appointment existing = appointmentMapper.selectOne(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getRequestId, request.getRequestId()));
        if (existing != null) return AppointmentIdempotency.verify(existing, request);

        scheduleConstraints.lockDay(request.getAppointmentDate());

        // Lock the current daily record; manual schedule edits must use the same row lock.
        StaffSchedule schedule = staffScheduleMapper.selectForUpdate(request.getStaffId(), request.getAppointmentDate());
        SpaService service = spaServiceMapper.selectById(request.getServiceId());
        Staff staff = staffMapper.selectById(request.getStaffId());
        if (service == null || !Integer.valueOf(1).equals(service.getStatus())
                || service.getDurationMinutes() == null || service.getDurationMinutes() <= 0
                || service.getPrice() == null || service.getPrice().signum() < 0
                || staff == null || !Integer.valueOf(1).equals(staff.getStatus())
                || !staffService.supportsService(request.getStaffId(), request.getServiceId())) {
            throw new BusinessException("服务或技师当前不可预约");
        }
        LocalDateTime start = request.getAppointmentDate().atTime(request.getStartTime());
        if (!start.isAfter(LocalDateTime.now(ScheduleAvailabilityPolicy.BUSINESS_ZONE))) {
            throw new BusinessException("不能预约过去的时间");
        }
        ScheduleAvailabilityPolicy.requireAvailable(schedule, start, service.getDurationMinutes());
        scheduleConstraints.requireAvailable(request.getStaffId(), start, service.getDurationMinutes());
        LocalDateTime end = start.plusMinutes(service.getDurationMinutes());
        // Every occupied slot reserves its complete 30-minute bucket, including a partial final bucket.
        // Looking back 30 minutes also protects legacy off-grid bucket starts.
        if (!appointmentSlotMapper.selectList(new LambdaQueryWrapper<AppointmentSlot>()
                .eq(AppointmentSlot::getStaffId, request.getStaffId())
                .gt(AppointmentSlot::getSlotTime, start.minusMinutes(30))
                .lt(AppointmentSlot::getSlotTime, end)).isEmpty()) {
            throw new BusinessException("该时间段已被预约，请重新选择");
        }
        Appointment appointment = new Appointment();
        appointment.setUserId(request.getUserId());
        appointment.setServiceId(request.getServiceId());
        appointment.setStaffId(request.getStaffId());
        appointment.setAppointmentDate(request.getAppointmentDate());
        appointment.setStartTime(request.getStartTime());
        appointment.setEndTime(end.toLocalTime());
        appointment.setAmount(service.getPrice());
        appointment.setRemark(request.getRemark());
        appointment.setStatus(1);
        appointment.setRequestId(request.getRequestId());
        appointment.setAppointmentNo(UUID.randomUUID().toString().replace("-", ""));
        appointmentMapper.insert(appointment);
        for (LocalDateTime bucket = start; bucket.isBefore(end); bucket = bucket.plusMinutes(30)) {
            AppointmentSlot slot = new AppointmentSlot();
            slot.setAppointmentId(appointment.getId());
            slot.setStaffId(request.getStaffId());
            slot.setSlotTime(bucket);
            appointmentSlotMapper.insert(slot);
        }
        orders.ensureForAppointment(appointment);
        return appointment;
    }
}
