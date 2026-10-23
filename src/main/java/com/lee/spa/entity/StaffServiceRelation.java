package com.lee.spa.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("staff_service")
public class StaffServiceRelation {

    private Long id;

    private Long staffId;

    private Long serviceId;

    private LocalDateTime createdAt;

}
