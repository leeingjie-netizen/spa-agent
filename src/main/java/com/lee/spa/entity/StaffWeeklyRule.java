package com.lee.spa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("staff_weekly_rule") // 告诉 MyBatis-Plus，这个 Java 类对应哪张数据库表。
public class StaffWeeklyRule {
    @TableId(type = IdType.ASSIGN_ID) // 明确使用 MyBatis-Plus 的雪花 ID 生成策略。
    private Long id;

    private Long staffId;

    private Integer dayOfWeek;

    private Integer isWorking;

    private LocalTime startTime;

    private LocalTime endTime;

    private LocalTime breakStartTime;

    private LocalTime breakEndTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
