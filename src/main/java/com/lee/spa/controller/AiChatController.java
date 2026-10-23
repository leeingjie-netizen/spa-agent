package com.lee.spa.controller;

import com.lee.spa.common.result.Result;
import com.lee.spa.dto.AiChatRequest;
import com.lee.spa.service.AiChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/ai/")
@RestController
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")

    public Result<String> chat(@RequestBody AiChatRequest request){

        String answer = aiChatService.chat(
                request.getConversationId(),
                request.getRequestId(),
                request.getMessage()
                );

        return Result.success(answer);
    }

}
