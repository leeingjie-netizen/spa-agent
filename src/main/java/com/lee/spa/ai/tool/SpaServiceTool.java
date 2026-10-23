package com.lee.spa.ai.tool;


import com.lee.spa.entity.SpaService;
import com.lee.spa.service.SpaServiceService;
import com.lee.spa.service.StaffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 服务项目Tool
 * 负责：把这个业务能力“包装成 LLM 可以使用的工具”。
 *
 * SpaServiceTool
 *        ↓
 * SpaServiceService.getAvailableServices()
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpaServiceTool {

    private final SpaServiceService spaServiceService;
    private final StaffService staffService;

    // Spring AI 1.x 官方支持直接通过 @Tool 把 Java 方法声明为模型工具，也支持稍后通过 .tools(...) 把它提供给 ChatClient。
    /*
     * @Tool 注解：告诉 Spring AI：这个普通 Java 方法，可以作为一个大模型 Tool。
     * name = "getServiceList"这才是模型看到的：工具名称
     * 以后模型返回类似：tool = getServiceList
     * Spring AI 才知道：要执行这个 Java 方法。
     *
     * description这个甚至比名字更加重要：
     * 因为 LLM 不会像 Java 程序员一样看你的源码。
     * 它不知道：getServiceList()到底是干什么的。
     * Spring AI 会向模型提供类似这样的 Tool Definition
     *
     * 用户说：“你们这里有什么护理项目？”
     * 模型看到：
     * 用户问题：
            查询护理项目

            Tool description：
            查询护理项目时使用
     *
     * 就有可能判断：应该调用 getServiceList
     *
     * 官方文档也特别强调 Tool Description 很重要，因为模型就是依靠名字、描述和参数 Schema 判断什么时候使用工具。
     *
     * 所以以后你写 Tool 时：description 不是给程序员看的注释，而是给 LLM 看的使用说明书。
     */
    @Tool(
            name = "getServiceList",
            description = "查询系统当前所有可用的SPA服务项目。当用户询问有哪些服务、护理项目、SPA项目时使用。"
    )

    public List<SpaService> getServiceList(){

        log.info("AI Tool 被调用：getServiceList");
        return spaServiceService.getAvailableServices();
    }


    @Tool(
            name = "getServicesByStaff",
            description = "根据技师ID或名字查询该技师可以提供的服务项目。当用户询问某个技师可以提供哪些服务时使用。"
    )
    public List<SpaService> getServicesByStaff(
            @ToolParam(description = "技师ID")
            Long staffId){

        log.info("AI Tool 被调用：getServicesByStaff，参数：{}", staffId);
        return staffService.getServicesById(staffId);
    }



}
