package com.lee.spa.controller;

import com.lee.spa.common.result.Result;
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

        return Result.success(appointmentService.createAppointment(request));
    }

    @GetMapping("/user/{userId}")
    public Result<List<Appointment>> getAppointmentsByUserId(@PathVariable Long userId) {
        return Result.success(appointmentService.getAppointmentsByUserId(userId));
    }

    @GetMapping("/user/{userId}/summaries")
    public Result<List<AppointmentSummaryVO>> getAppointmentSummaries(@PathVariable Long userId) {
        return Result.success(appointmentService.getAppointmentSummariesByUserId(userId));
    }


    @PostMapping("/{appointmentId}/cancel")
    public Result<Boolean> cancelAppointmentByAptId(@PathVariable Long appointmentId, @RequestParam Long userId){
        return Result.success(appointmentService.cancelAppointmentByAptId(appointmentId, userId));
    }
}
