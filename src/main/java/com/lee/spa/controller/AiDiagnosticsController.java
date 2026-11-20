package com.lee.spa.controller;
import com.lee.spa.auth.AuthContext;
import com.lee.spa.common.result.Result;
import com.lee.spa.config.AiRuntimeConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class AiDiagnosticsController {
 private final AiRuntimeConfiguration configuration;
 @GetMapping("/api/admin/ai/status") public Result<?> status(){AuthContext.requireRole("ADMIN");return Result.success(configuration.status());}
}
