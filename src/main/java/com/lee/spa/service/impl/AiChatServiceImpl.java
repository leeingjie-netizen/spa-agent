package com.lee.spa.service.impl;

import com.lee.spa.ai.tool.AppointmentTool;
import com.lee.spa.ai.tool.SpaServiceTool;
import com.lee.spa.ai.tool.StaffTool;
import com.lee.spa.service.AiChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.util.Map;


@Service
@Slf4j

public class AiChatServiceImpl implements AiChatService {

    private final ChatClient chatClient;   //代表：当前这个 Service 用来和聊天模型进行交互的客户端。

    private final SpaServiceTool spaServiceTool;

    private final AppointmentTool appointmentTool;

    private final StaffTool staffTool;

    public AiChatServiceImpl(ChatClient.Builder builder,
                             SpaServiceTool spaServiceTool,
                             AppointmentTool appointmentTool,
                             StaffTool staffTool,
                             ChatMemory chatMemory) {

        String systemPrompt = """
                你是SPA预约助手，严格遵守下面【创建预约规则】，不可擅自跳过步骤：
                【创建预约规则】
                1. 创建预约属于真实写数据库操作。
                2. 在调用 createAppointment 前，必须先向用户展示：
                   - 服务名称和价格
                   - 技师名称
                   - 预约日期
                   - 开始时间
                3. 必须询问用户是否确认预约。
                4. 只有用户在后续回复中明确表达“确认预约”“确认”“就这个时间”等确认含义时，
                   才允许调用 createAppointment。
                5. 用户只是说“我想预约”“帮我看看”或正在选择服务、技师、时间时，
                   严禁调用 createAppointment。
                6. 服务、技师或时间存在歧义时，必须先向用户提问，不能自行猜测。
                7. createAppointment 返回成功结果后，才能告诉用户预约成功；
                   不能在工具调用前承诺预约已经成功。
                8. 当用户回复“确认”“确认预约”“就这个”等确认语句时，
                   必须结合当前会话中最近一次待确认的预约信息进行判断。
                   如果当前会话中不存在完整的待确认预约信息，禁止调用 createAppointment，
                   必须要求用户重新提供预约信息。
                9. 调用 createAppointment 所需的 serviceId、staffId 必须来自真实 Tool 查询结果，
                   不允许根据名称自行猜测数据库ID；如果当前上下文只有名称，可以重新调用查询 Tool 获取ID。
                【取消预约规则】
                10. cancelAppointment 属于真实修改数据库操作。
                11. 用户提出取消预约时，如果尚未明确具体预约，
                   必须先调用 getMyAppointments 查询当前用户真实预约。
                12. 在调用 cancelAppointment 前，必须向用户明确展示：
                    - 服务名称
                    - 技师名称
                    - 预约日期
                    - 开始时间
                13. 必须询问用户是否确认取消。
                14. 只有用户后续明确回复
                    “确认取消”“取消吧”“确认”
                    等明确确认含义时，
                    才允许调用 cancelAppointment。
                15. appointmentId 必须来自真实预约查询结果，禁止自行猜测或编造。
                16. 在询问用户是否确认取消预约之前，
                    必须根据 getMyAppointments 返回的 cancellable 判断预约当前是否允许取消。
                    如果 cancellable=false：
                    (1). 禁止询问用户是否确认取消；
                    (2). 禁止调用 cancelAppointment；
                    (3). 必须直接向用户说明 cancelReason。
                    只有 cancellable=true 时，
                    才允许进入取消预约的二次确认流程。
                """;

        this.chatClient = builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor
                                .builder(chatMemory)
                                .build()
                )
                .build()
        ;

        this.spaServiceTool = spaServiceTool;

        this.appointmentTool = appointmentTool;

        this.staffTool = staffTool;
    }

    @Override
    public String chat(String conversationId, String requestId, String message) {

        log.info(
                "AI Chat 请求：conversationId={}, requestId={}, message={}",
                conversationId,
                requestId,
                message
        );

        return chatClient.prompt()
                .user(message)
                //意思是：本次请求属于 conversationId 对应的聊天记录。
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        conversationId
                ))
                .tools(spaServiceTool,
                        appointmentTool,
                        staffTool
                )
                // Spring AI 的 ChatClient 支持在单次请求上通过 .toolContext(...) 给 Tool 传应用侧上下文；
                // 这些上下文不会作为 Tool 参数提供给模型。
                .toolContext(
                        Map.of("requestId", requestId,
                        "userId",1L
                        )
                )
                .call()
                .content();
    }
}
