package com.example.chat.service;

import com.example.chat.dto.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ChatService {

    ChatResponse chat(ChatRequest request);

    SseEmitter chatStream(ChatRequest request);

    List<ConversationResponse> listConversations();

    List<ChatMessageResponse> getMessages(String conversationId);

    void deleteConversation(String conversationId);

    ConversationResponse renameConversation(String conversationId, String title);
}
