package com.lee.spa.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("spa_service")
public class SpaService {

    private Long id;

    private String name;

    private String description;

    private Integer durationMinutes;

    private BigDecimal price;

    private String coverUrl;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}