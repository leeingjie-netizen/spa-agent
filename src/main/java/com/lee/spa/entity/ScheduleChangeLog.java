package com.lee.spa.entity;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
@Data
@TableName("schedule_change_log")
public class ScheduleChangeLog {
private Long id;
private Long staffId;
private java.time.LocalDate workDate;
private String previousValue;
private String newValue;
private String reason;
private Long operatorId;
private java.time.LocalDateTime createdAt;
}
