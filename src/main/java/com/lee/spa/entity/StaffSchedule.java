package com.lee.spa.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("staff_schedule")
public class StaffSchedule {
    private Long id;

    private Long staffId;

    private LocalDate workDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * AUTO：自动生成
     * MANUAL：管理员手工维护
     */
    private String source;

    /**
     * 当天午休时间
     */
    private LocalTime breakStartTime;

    private LocalTime breakEndTime;
}
