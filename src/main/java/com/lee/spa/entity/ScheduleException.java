package com.lee.spa.entity;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
@Data
@TableName("schedule_exception")
public class ScheduleException {
@TableField(exist=false)
private Boolean revoked;
private Long id;
private Long staffId;
private java.time.LocalDate workDate;
private java.time.LocalTime startTime;
private java.time.LocalTime endTime;
private String reason;
private Long createdBy;
private java.time.LocalDateTime createdAt;
}
