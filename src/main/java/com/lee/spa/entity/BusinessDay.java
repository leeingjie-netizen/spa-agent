package com.lee.spa.entity;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
@Data
@TableName("business_day")
public class BusinessDay {
@TableId(value="work_date",type=IdType.INPUT)
private java.time.LocalDate workDate;
private Boolean closed;
private java.time.LocalTime openTime;
private java.time.LocalTime closeTime;
private String reason;
private Long updatedBy;
private java.time.LocalDateTime updatedAt;
}
