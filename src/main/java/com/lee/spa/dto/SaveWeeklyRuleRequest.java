package com.lee.spa.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class SaveWeeklyRuleRequest {

    @NotNull(message = "是否上班不能为空")
    @Min(value = 0, message = "是否上班只能为0或1")
    @Max(value = 1, message = "是否上班只能为0或1")
    private Integer isWorking;

    private LocalTime startTime;

    private LocalTime endTime;

    private LocalTime breakStartTime;

    private LocalTime breakEndTime;
}
