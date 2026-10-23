package com.lee.spa.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data

public class Staff {
    private Long id;

    private String name;

    private String avatarUrl;

    private String introduction;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
