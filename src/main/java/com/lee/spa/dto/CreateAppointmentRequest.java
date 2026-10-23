package com.lee.spa.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data

/**
 * 负责：接收前端的数据。
 * 它不负责 SQL，也不负责判断有没有冲突。这个类只负责数据的接收。
 */
public class CreateAppointmentRequest {

    private Long userId;

    private Long serviceId;

    private Long staffId;

    private LocalDate appointmentDate;

    private LocalTime startTime;

    private String remark;

    private String requestId;

}
