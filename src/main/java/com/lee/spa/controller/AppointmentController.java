package com.lee.spa.controller;

import com.lee.spa.common.result.Result;
import com.lee.spa.auth.AuthContext;
import com.lee.spa.auth.AuthException;
import com.lee.spa.dto.CreateAppointmentRequest;
import com.lee.spa.entity.Appointment;
import com.lee.spa.service.AppointmentService;
import com.lee.spa.vo.AppointmentSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final AppointmentService appointmentService;

    @PostMapping
    public Result<Appointment> createAppointment(
            @RequestBody CreateAppointmentRequest request) {

        request.setUserId(AuthContext.requireUserId());
        return Result.success(appointmentService.createAppointment(request));
    }

    @GetMapping("/user/{userId}")
    public Result<List<Appointment>> getAppointmentsByUserId(@PathVariable Long userId) {
        requireOwner(userId);
        return Result.success(appointmentService.getAppointmentsByUserId(userId));
    }

    @GetMapping("/user/{userId}/summaries")
    public Result<List<AppointmentSummaryVO>> getAppointmentSummaries(@PathVariable Long userId) {
        requireOwner(userId);
        return Result.success(appointmentService.getAppointmentSummariesByUserId(userId));
    }


    @PostMapping("/{appointmentId}/cancel")
    public Result<Boolean> cancelAppointmentByAptId(@PathVariable Long appointmentId, @RequestParam(required=false) Long userId){
        return Result.success(appointmentService.cancelAppointmentByAptId(appointmentId, AuthContext.requireUserId()));
    }

    @GetMapping("/me")
    public Result<List<AppointmentSummaryVO>> mine() {
        return Result.success(appointmentService.getAppointmentSummariesByUserId(AuthContext.requireUserId()));
    }

    @GetMapping("/{appointmentId}")
    public Result<AppointmentSummaryVO> detail(@PathVariable Long appointmentId) {
        return Result.success(appointmentService.getAppointmentSummariesByUserId(AuthContext.requireUserId()).stream()
                .filter(item -> appointmentId.equals(item.getAppointmentId())).findFirst()
                .orElseThrow(() -> new AuthException(404, "预约不存在")));
    }

    private void requireOwner(Long userId) {
        if (!AuthContext.requireUserId().equals(userId)) throw new AuthException(403, "不能读取其他用户的预约");
    }
}
