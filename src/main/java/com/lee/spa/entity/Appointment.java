package com.lee.spa.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data

/**
 * 负责：
 * Java 对象 ↔ 数据库表。
 * 其他实体类也是如此
 * Appointment
 *         ↕
 * appointment（数据库表）
 */
public class Appointment {

    private Long id;   //   预约的id

    private String appointmentNo;    // 预约编号

    private Long userId;      // 预约的用户id

    private Long serviceId;   // 预约的服务id

    private Long staffId;    // 预约的员工id

    private LocalDate appointmentDate; // 预约日期

    private LocalTime startTime; //     预约开始时间

    private LocalTime endTime;   // 预约结束时间

    private BigDecimal amount;   // 预约金额

    private Integer status;   // 预约状态（例如：0-待确认，1-已预约，2-已完成，3-已取消）

    private String remark;   // 预约备注

    private LocalDateTime createdAt;  // 创建时间

    private LocalDateTime updatedAt;  // 更新时间

    private String requestId;   // 预约请求编号
}
