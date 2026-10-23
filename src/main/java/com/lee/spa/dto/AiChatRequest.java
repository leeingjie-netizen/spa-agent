package com.lee.spa.dto;


import lombok.Data;

/**
 * 职责：接收前端发送给AI的消息
 *
 * 以后 Postman 请求：
 * {
 *   "message": "你好"
 * }
 * Spring MVC 就会转换成：AiChatRequest request
 * 然后：request.getMessage()
 * 得到：你好
 */
@Data
public class AiChatRequest {

    private String message;

    /**
     * conversationId
     * 一整个聊天不变
     *
     * requestId
     * 每一次用户提交都不同
     * 但同一次请求重试必须相同
     */
    private String conversationId;  // 这是哪一段对话

    private String requestId;  // 这是哪一次 HTTP / 用户操作？

}
