package com.lee.spa.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("appointment_slot")
public class AppointmentSlot {

    private Long id;

    private Long appointmentId;

    private Long staffId;

    private LocalDateTime slotTime;

    private LocalDateTime createdAt;

}
