package com.lee.spa.controller;

import com.lee.spa.common.result.Result;
import com.lee.spa.auth.AuthContext;
import com.lee.spa.dto.SaveWeeklyRuleRequest;
import com.lee.spa.entity.SpaService;
import com.lee.spa.entity.Staff;
import com.lee.spa.entity.StaffSchedule;
import com.lee.spa.entity.StaffWeeklyRule;
import com.lee.spa.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    /**
     * 获取所有员工列表
     */
    @GetMapping
    public Result<List<Staff>> list(){
        return Result.success(staffService.getStaffList());
    }

    /**
     * 根据id获取员工信息
     */
    @GetMapping("/{id}")
    public Result<Staff> getStaffById(@PathVariable Long id) {
        return Result.success(staffService.getStaffById(id));
    }

    /**
     * 根据员工id获取服务列表
     */
    @GetMapping("/{staffId}/services")
    public Result<List<SpaService>> getStaffServices(@PathVariable Long staffId){
        List<SpaService> services = staffService.getServicesById(staffId);
        return Result.success(services);
    }

    /**
     * 获取员工日程
     */
    @GetMapping("/{staffId}/schedule")
    public Result<StaffSchedule> getStaffSchedule(@PathVariable Long staffId, @RequestParam LocalDate date){
        return Result.success(staffService.getStaffSchedule(staffId, date));
    }
    /**
     * 获取员工可用时间槽
     */
    @GetMapping("/{staffId}/availableSlots")
    public Result<List<LocalTime>> getAvailableSlots(@PathVariable Long staffId, @RequestParam LocalDate date, @RequestParam Long serviceId){
        List<LocalTime> availableSlots = staffService.getAvailableSlots(staffId, date, serviceId);
        return Result.success(availableSlots);
    }

    /**
     * 根据服务id获取员工列表
     */
    @GetMapping("/{serviceId}/staff")
    public Result<List<Staff>> getStaffByService(@PathVariable Long serviceId){
        List<Staff> availableStaffs = staffService.getStaffByService(serviceId);

        return Result.success(availableStaffs);
    }

    @GetMapping("/{staffId}/weekly-rules")
    public Result<List<StaffWeeklyRule>> getWeeklyRules(@PathVariable Long staffId){

        List<StaffWeeklyRule> rules = staffService.getWeeklyRulesByStaffId(staffId);

        return Result.success(rules);

    }

    @PutMapping("/{staffId}/weekly-rules/{dayOfWeek}")
    public Result<StaffWeeklyRule> saveOrUpdateWeeklyRule(
            @PathVariable Long staffId,
            @PathVariable Integer dayOfWeek,
            // @RequestBody	把 JSON 转换为 Java DTO
            // @Valid	触发 DTO 上的 Bean Validation 校验
            // 特别注意 @Valid：它主要负责你在 DTO 上通过 @NotNull、@Min、@Max 等声明的基础约束。
            //像“午休必须处于工作时间内”这样的组合条件，仍然由 Service 负责。
            @Valid @RequestBody SaveWeeklyRuleRequest request) {

        AuthContext.requireRole("ADMIN");
        StaffWeeklyRule rule = staffService.saveOrUpdateWeeklyRule(staffId, dayOfWeek, request);

        return Result.success(rule);
    }

}
