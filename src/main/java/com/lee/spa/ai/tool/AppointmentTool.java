package com.lee.spa.ai.tool;


import com.lee.spa.dto.CreateAppointmentRequest;
import com.lee.spa.entity.Appointment;
import com.lee.spa.service.AppointmentService;
import com.lee.spa.service.StaffService;
import com.lee.spa.vo.AppointmentSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 预约 / 技师时间资源
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentTool {

    private final StaffService staffService;

    private final AppointmentService appointmentService;


    @Tool(
            name = "getAvailableTime",
            description = "查询指定技师在指定日期指定服务时的真实可预约开始时间。用户询问" +
                    "某位技师某天有哪些可预约时间时使用。"
    )

    public List<LocalTime> getAvailableTime(

            @ToolParam(description = "技师ID，例如2号技师传2")
            Long staffId,

            @ToolParam(description = "SPA服务项目ID，例如3号服务传3")
            Long serviceId,

            @ToolParam(description = "预约日期，必须使用yyyy-MM-dd格式。例如2026-09-25")
            String date){

        log.info("AI Tool 被调用：getAvailableTime, staffId={}, serviceId={}, date={}",
                staffId, serviceId, date);

        // Tool 内部只做非常薄的一层转换，将 String 类型的 date 转换为 LocalDate 类型
        LocalDate appointmentDate = LocalDate.parse(date);

        return staffService.getAvailableSlots(staffId, appointmentDate, serviceId);


    }


    @Tool(
            name = "createAppointment",
            description = """
                    创建真实预约并写入数据库。
                    只能当服务、技师、日期和开始时间都已明确，
                    并且用户在看到完整预约信息后明确回复“确认预约”或同等含义时才能调用。
                    用户尚未确认、信息不完整或存在多个候选项时严禁调用。
                    调用前必须先通过查询工具确认技师支持该服务，并确认该时间可以预约。
                    """
    )
    public Appointment createAppointment(

            @ToolParam(description = "服务项目ID，必须来自服务查询结果")
            Long serviceId,

            @ToolParam(description = "技师ID，必须来自技师查询结果")
            Long staffId,

            @ToolParam(description = "预约日期，格式必须为yyyy-MM-dd，例如2026-09-25")
            String appointmentDate,

            @ToolParam(description = "预约开始时间，格式必须为HH:mm，例如15:00")
            String startTime,


            ToolContext toolContext  //ToolContext 不是 LLM 需要填写的 Tool 参数。
            /*
            模型仍然只看：
                serviceId
                staffId
                appointmentDate
                startTime

                ToolContext 是 Spring AI 在真正执行 Java 方法时额外交给你的。
                官方就是把用户 ID、tenant ID、请求范围信息等作为它的典型用途。
             */
    ){

        log.info(
                "AI Tool 被调用：createAppointment, serviceId={}, staffId={}, date={}, startTime={}",
                serviceId,
                staffId,
                appointmentDate,
                startTime
        );

        CreateAppointmentRequest request = new CreateAppointmentRequest();

        // 当前暂时使用固定的演示用户
        request.setUserId(1L);

        request.setServiceId(serviceId);
        request.setStaffId(staffId);
        request.setAppointmentDate(LocalDate.parse(appointmentDate));
        request.setStartTime(LocalTime.parse(startTime));

        String requestId = (String) toolContext
                .getContext()
                .get("requestId");
        request.setRequestId("AI-" + requestId);

        request.setRemark("通过AI助手创建");

        return appointmentService.createAppointment(request);

    }


    @Tool(
            name = "getMyAppointments",
            description = "查询当前用户真实预约记录。\n" +
                    "      返回预约服务、技师、日期、时间。\n" +
                    "      等用户有取消预约需求的时候再说明当前是否允许取消。"+
                    "      当用户查询自己的预约、准备取消预约时使用。"
    )
    public List<AppointmentSummaryVO> getMyAppointments(ToolContext toolContext){

        Long userId = (Long)toolContext
                .getContext()
                .get("userId");

        log.info("AI Tool 被调用：getMyAppointments, userId={}", userId);
        return appointmentService.getAppointmentSummariesByUserId(userId);
    }


    @Tool(
            name = "cancelAppointment",
            description = "取消指定预约。当用户想取消某个预约但没有提供预约ID时使用。并且appointment.userId等于当前 userId"
    )
    public boolean cancelAppointment(
            @ToolParam(description = "预约ID")
            Long appointmentId,

            ToolContext toolContext
    ){

        log.info("AI Tool 被调用：cancelAppointment, appointmentId={},userId={}", appointmentId, (Long)toolContext.getContext().get("userId"));
        appointmentService.cancelAppointmentByAptId(appointmentId, (Long)toolContext.getContext().get("userId"));
        return true;
    }

}
