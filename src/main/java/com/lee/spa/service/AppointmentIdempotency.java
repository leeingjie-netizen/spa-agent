package com.lee.spa.service;

import com.lee.spa.common.exception.BusinessException;
import com.lee.spa.dto.CreateAppointmentRequest;
import com.lee.spa.entity.Appointment;
import java.util.Objects;

public final class AppointmentIdempotency {
    private AppointmentIdempotency() { }

    public static Appointment verify(Appointment existing, CreateAppointmentRequest request) {
        if (!Objects.equals(existing.getUserId(), request.getUserId())
                || !Objects.equals(existing.getStaffId(), request.getStaffId())
                || !Objects.equals(existing.getServiceId(), request.getServiceId())
                || !Objects.equals(existing.getAppointmentDate(), request.getAppointmentDate())
                || !Objects.equals(existing.getStartTime(), request.getStartTime())
                || !Objects.equals(existing.getRemark(), request.getRemark())) {
            throw new BusinessException("请求标识已使用，请勿用于其他预约");
        }
        return existing;
    }
}
