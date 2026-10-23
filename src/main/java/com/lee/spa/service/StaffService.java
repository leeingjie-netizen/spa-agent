package com.lee.spa.service;


import com.lee.spa.entity.SpaService;
import com.lee.spa.entity.Staff;
import com.lee.spa.entity.StaffSchedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface StaffService {
    /**
     * 获取员工列表
     * @return
     */
    List<Staff> getStaffList();

    /**
     * 根据 id 获取员工信息
     * @param id
     * @return
     */
    Staff getStaffById(Long id);

    /**
     * 根据员工 id 获取服务列表
     *
     * 为什么返回 List<SpaService>？
     * 因为最终要返回的是：
     *      精油 SPA
     *      肩颈护理
     * 这些都属于：SpaService。而且不止一个。
     * 所以：List<SpaService>
     * @param staffId
     * @return
     */
    List<SpaService> getServicesById(Long staffId);

    /**
     * 技师排班查询
     */
    StaffSchedule getStaffSchedule(Long staffId, LocalDate date);

    /**
     * 获取可用时间槽
     */
    List<LocalTime> getAvailableSlots(Long staffId, LocalDate date, Long serviceId);

    /**
     * 技师是否提供该服务
     */
    boolean supportsService(Long staffId, Long serviceId);


    /**
     * 根据服务id获取都有哪些技师
     */
    List<Staff> getStaffByService(Long serviceId);
}
