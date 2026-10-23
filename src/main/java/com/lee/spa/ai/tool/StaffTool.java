package com.lee.spa.ai.tool;

import com.lee.spa.entity.Staff;
import com.lee.spa.service.StaffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StaffTool {

    private final StaffService staffService;

    @Tool(
            name = "getStaffList",
            description = "查询系统当前都有哪些技师或员工。当用户询问有哪些技师或员工时使用。"
    )

    public List<Staff> getStaffList(){

        log.info("AI Tool 被调用：getStaffList");

        return staffService.getStaffList();

    }


    @Tool(
            name = "getStaffByService",
            description = "查询真实支持指定服务项目的所有可用技师。当用户询问哪些技师会做某项服务、某服务可以由谁提供时使用。"
    )

    public List<Staff> getStaffByService(

            @ToolParam(description = "SPA服务项目ID，如果用户说3号服务就传3，如果说的是服务名称就根据数据库字段对应的ID传入")
            Long serviceId){

        log.info("AI Tool 被调用：getStaffByService");

        return staffService.getStaffByService(serviceId);
    }


}
