package com.lee.spa.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Appointment Entity代表的是：
 *      appointment 表的数据。
 *
 * AppointmentSummaryVO代表的是：
 *      我要展示给前端/AI看的预约信息
 *
 *      这是两种不同的职责
 */
@Data
public class AppointmentSummaryVO {

        private Long appointmentId;

        private String appointmentNo;

        private Long serviceId;

        private String serviceName;

        private Long staffId;

        private String staffName;

        private LocalDate appointmentDate;

        private LocalTime startTime;

        private LocalTime endTime;

        private BigDecimal amount;

        private Integer status;

        private Boolean cancellable;

        private String cancelReason;

}
