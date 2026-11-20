package com.lee.spa.entity;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
@Data
@TableName("staff_leave")
public class StaffLeave {
private Long id;
private Long staffId;
private java.time.LocalDate workDate;
private Boolean allDay;
private java.time.LocalTime startTime;
private java.time.LocalTime endTime;
private String reason;
private String status;
private Long requestedBy;
private Long reviewedBy;
private String reviewReason;
private java.time.LocalDateTime reviewedAt;
private java.time.LocalDateTime createdAt;
}
