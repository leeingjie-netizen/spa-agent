package com.lee.spa.controller;

import com.lee.spa.common.result.Result;
import com.lee.spa.entity.SpaService;
import com.lee.spa.entity.Staff;
import com.lee.spa.entity.StaffSchedule;
import com.lee.spa.service.StaffService;
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
     * @return
     */
    @GetMapping
    public Result<List<Staff>> list(){
        return Result.success(staffService.getStaffList());
    }

    /**
     * 根据id获取员工信息
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result<Staff> getStaffById(@PathVariable Long id) {
        return Result.success(staffService.getStaffById(id));
    }

    /**
     * 根据员工id获取服务列表
     * @param staffId
     * @return
     */
    @GetMapping("/{staffId}/services")
    public Result<List<SpaService>> getStaffServices(@PathVariable Long staffId){
        List<SpaService> services = staffService.getServicesById(staffId);
        return Result.success(services);
    }

    /**
     * 获取员工日程
     * @param staffId
     * @param date
     * @return
     */
    @GetMapping("/{staffId}/schedule")
    public Result<StaffSchedule> getStaffSchedule(@PathVariable Long staffId, @RequestParam LocalDate date){
        return Result.success(staffService.getStaffSchedule(staffId, date));
    }
    /**
     * 获取员工可用时间槽
     * @param staffId
     * @param date
     * @param serviceId
     * @return
     */
    @GetMapping("/{staffId}/availableSlots")
    public Result<List<LocalTime>> getAvailableSlots(@PathVariable Long staffId, @RequestParam LocalDate date, @RequestParam Long serviceId){
        List<LocalTime> availableSlots = staffService.getAvailableSlots(staffId, date, serviceId);
        return Result.success(availableSlots);
    }

    /**
     * 根据服务id获取员工列表
     * @param serviceId
     * @return
     */
    @GetMapping("/{serviceId}/staff")
    public Result<List<Staff>> getStaffByService(@PathVariable Long serviceId){
        List<Staff> availableStaffs = staffService.getStaffByService(serviceId);

        return Result.success(availableStaffs);
    }
}
