package com.lee.spa.service;

import com.lee.spa.dto.CreateAppointmentRequest;
import com.lee.spa.entity.Appointment;
import com.lee.spa.vo.AppointmentSummaryVO;

import java.util.List;


public interface AppointmentService {
    /**
     * 创建预约
     */
    Appointment createAppointment(CreateAppointmentRequest request);


    /**
     * 查询我的预约
     */
    List<Appointment> getAppointmentsByUserId(Long userId);

    /**
     * 查询我的预约summary
     * 不破坏已有功能 + 增加一个更适合展示/AI的返回结果
     */
    List<AppointmentSummaryVO> getAppointmentSummariesByUserId(Long userId);

    /**
     * 取消预约
     */
    Boolean cancelAppointmentByAptId(Long appointmentId,Long userId);


}
