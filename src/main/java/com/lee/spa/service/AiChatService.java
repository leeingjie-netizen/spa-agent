package com.lee.spa.service;


/**
 * 负责：收到用户消息后，怎么获得 AI 回答。
 */
public interface AiChatService {

    String chat(String conversationId,String requestId,String message);

}
