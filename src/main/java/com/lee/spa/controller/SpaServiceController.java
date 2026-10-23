package com.lee.spa.controller;

import com.lee.spa.common.result.Result;
import com.lee.spa.entity.SpaService;
import com.lee.spa.service.SpaServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class SpaServiceController {

    private final SpaServiceService spaServiceService;

    @GetMapping
    public Result<List<SpaService>> list() {

        return Result.success(
                spaServiceService.getAvailableServices()
        );
    }

    @GetMapping("/{id}")
    public Result<SpaService> detail(@PathVariable Long id) {

        return Result.success(
                spaServiceService.getServiceById(id)
        );
    }
}