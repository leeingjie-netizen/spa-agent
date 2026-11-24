package com.lee.spa.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.redisson.api.RedissonClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Private container readiness route; reverse proxy denies external access. */
@RestController @RequiredArgsConstructor
public class HealthController {
 private final JdbcTemplate jdbc; private final RedissonClient redis;
 @GetMapping("/health/ready") public ResponseEntity<Map<String,String>> ready(){
  try {jdbc.queryForObject("SELECT 1",Integer.class); redis.getBucket("spa:health:unused").isExists();
   return ResponseEntity.ok(Map.of("status","UP"));
  }catch(RuntimeException unavailable){return ResponseEntity.status(503).body(Map.of("status","DOWN"));}
 }
}
