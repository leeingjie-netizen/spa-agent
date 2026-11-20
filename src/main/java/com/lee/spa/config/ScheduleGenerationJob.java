package com.lee.spa.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lee.spa.entity.Staff;
import com.lee.spa.mapper.StaffMapper;
import com.lee.spa.service.ScheduleGenerationService;
import com.lee.spa.vo.ScheduleGenerationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Enable explicitly only after reviewing weekly rules and generation results. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "spa.scheduling.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ScheduleGenerationJob {
    private final StaffMapper staff;
    private final ScheduleGenerationService generation;

    @EventListener(ApplicationReadyEvent.class)
    public void catchUpAfterRestart() {
        replenish();
    }

    @Scheduled(cron = "${spa.scheduling.cron:0 5 0 * * *}", zone = "Asia/Shanghai")
    public void replenish() {
        for (Staff technician : staff.selectList(new LambdaQueryWrapper<Staff>().eq(Staff::getStatus, 1))) {
            try {
                ScheduleGenerationResult result = generation.generateNext30Days(technician.getId());
                log.info("排班补齐 staffId={} generated={} skipped={} conflicts={} failed={}",
                        technician.getId(), result.getGenerated(), result.getSkipped(),
                        result.getConflicts(), result.getFailed());
            } catch (RuntimeException failure) {
                log.error("技师排班补齐失败 staffId={}", technician.getId(), failure);
            }
        }
    }
}
