package com.lee.spa.commerce;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import java.time.*;

@Component
@EnableScheduling
@ConditionalOnProperty(name="spa.orders.timeout-enabled",havingValue="true",matchIfMissing=true)
@RequiredArgsConstructor
@Slf4j
public class OrderTimeoutJob {
    private final JdbcTemplate jdbc;
    private final OrderService orders;
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void closeExpired() {
        for(Long id:jdbc.queryForList("SELECT id FROM spa_order WHERE status='WAITING_PAYMENT' AND expires_at<=? LIMIT 100",Long.class,LocalDateTime.now(ZoneId.of("Asia/Shanghai")))) {
            try { orders.closeExpired(id); } catch(RuntimeException failure) { log.warn("订单超时关闭失败 id={}",id,failure); }
        }
    }
}
