package com.lee.spa.controller;
import com.lee.spa.common.result.Result;
import com.lee.spa.dto.ScheduleManagementRequests.Leave;
import com.lee.spa.service.ScheduleManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/staff/leaves")
@RequiredArgsConstructor
public class StaffLeaveController {
 private final ScheduleManagementService service;
 @GetMapping public Result<?> list(){return Result.success(service.leaveList());}
 @PostMapping public Result<?> submit(@RequestBody Leave request){return Result.success(service.submit(request));}
 @PostMapping("/{id}/withdraw") public Result<?> withdraw(@PathVariable Long id){return Result.success(service.withdraw(id));}
}
