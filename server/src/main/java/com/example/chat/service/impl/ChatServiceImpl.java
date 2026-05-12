package com.example.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.chat.dto.*;
import com.example.chat.entity.ChatMessage;
import com.example.chat.entity.Conversation;
import com.example.chat.mapper.ChatMessageMapper;
import com.example.chat.mapper.ConversationMapper;
import com.example.chat.service.ChatService;
import com.example.chat.util.UserContext;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ChatServiceImpl implements ChatService {

    @Value("classpath:prompts/system-prompt.st")
    private Resource systemPromptResource;

    private final DeepSeekChatModel chatModel;
    private final ConversationMapper conversationMapper;
    private final ChatMessageMapper chatMessageMapper;

    public ChatServiceImpl(DeepSeekChatModel chatModel,
                           ConversationMapper conversationMapper,
                           ChatMessageMapper chatMessageMapper) {
        this.chatModel = chatModel;
        this.conversationMapper = conversationMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        Long userId = requireCurrentUser();
        String conversationId = resolveConversationId(request.conversationId());
        ensureConversationExists(userId, conversationId, request.message());

        chatMessageMapper.insert(new ChatMessage(conversationId, "user", request.message()));

        List<Message> messages = buildPromptMessages(conversationId);
        Prompt prompt = new Prompt(messages);
        var response = chatModel.call(prompt);
        String responseContent = response.getResult().getOutput().getText();

        chatMessageMapper.insert(new ChatMessage(conversationId, "assistant", responseContent));

        return ChatResponse.of(responseContent, conversationId, "deepseek-chat");
    }

    @Override
    public SseEmitter chatStream(ChatRequest request) {
        Long userId = requireCurrentUser();
        String conversationId = resolveConversationId(request.conversationId());
        ensureConversationExists(userId, conversationId, request.message());

        chatMessageMapper.insert(new ChatMessage(conversationId, "user", request.message()));

        List<Message> messages = buildPromptMessages(conversationId);
        Prompt prompt = new Prompt(messages);

        SseEmitter emitter = new SseEmitter(0L);
        StringBuilder fullResponse = new StringBuilder();

        chatModel.stream(prompt)
                .subscribe(
                        chatResponse -> {
                            String content = chatResponse.getResult().getOutput().getText();
                            if (content != null) {
                                fullResponse.append(content);
                                try {
                                    emitter.send(content);
                                } catch (IOException e) {
                                    throw new RuntimeException("SSE send failed", e);
                                }
                            }
                        },
                        emitter::completeWithError,
                        () -> {
                            if (!fullResponse.isEmpty()) {
                                chatMessageMapper.insert(new ChatMessage(conversationId, "assistant", fullResponse.toString()));
                            }
                            emitter.complete();
                        }
                );

        return emitter;
    }

    @Override
    public List<ConversationResponse> listConversations() {
        Long userId = requireCurrentUser();
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getUserId, userId)
               .orderByDesc(Conversation::getUpdatedAt);
        return conversationMapper.selectList(wrapper)
                .stream()
                .map(c -> new ConversationResponse(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt()))
                .toList();
    }

    @Override
    public List<ChatMessageResponse> getMessages(String conversationId) {
        Long userId = requireCurrentUser();
        verifyOwnership(userId, conversationId);

        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, conversationId)
               .orderByAsc(ChatMessage::getCreatedAt);
        return chatMessageMapper.selectList(wrapper)
                .stream()
                .map(m -> new ChatMessageResponse(m.getId(), m.getRole(), m.getContent()))
                .toList();
    }

    @Override
    public void deleteConversation(String conversationId) {
        Long userId = requireCurrentUser();
        verifyOwnership(userId, conversationId);

        LambdaQueryWrapper<ChatMessage> msgWrapper = new LambdaQueryWrapper<>();
        msgWrapper.eq(ChatMessage::getConversationId, conversationId);
        chatMessageMapper.delete(msgWrapper);
        conversationMapper.deleteById(conversationId);
    }

    @Override
    public ConversationResponse renameConversation(String conversationId, String title) {
        Long userId = requireCurrentUser();
        verifyOwnership(userId, conversationId);

        LambdaUpdateWrapper<Conversation> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Conversation::getId, conversationId)
               .eq(Conversation::getUserId, userId)
               .set(Conversation::getTitle, title)
               .set(Conversation::getUpdatedAt, LocalDateTime.now());
        conversationMapper.update(null, wrapper);

        Conversation conversation = conversationMapper.selectById(conversationId);
        return new ConversationResponse(conversation.getId(), conversation.getTitle(), conversation.getCreatedAt(), conversation.getUpdatedAt());
    }

    private Long requireCurrentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException("未登录");
        }
        return userId;
    }

    private void verifyOwnership(Long userId, String conversationId) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getId, conversationId)
               .eq(Conversation::getUserId, userId);
        if (conversationMapper.selectCount(wrapper) == 0) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }
    }

    private String resolveConversationId(String conversationId) {
        return (conversationId != null && !conversationId.isBlank())
                ? conversationId
                : UUID.randomUUID().toString();
    }

    private void ensureConversationExists(Long userId, String conversationId, String firstMessage) {
        if (conversationMapper.selectById(conversationId) == null) {
            Conversation conversation = new Conversation();
            conversation.setId(conversationId);
            conversation.setUserId(userId);
            conversation.setTitle(extractTitle(firstMessage));
            conversation.setCreatedAt(LocalDateTime.now());
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationMapper.insert(conversation);
        }
    }

    private String extractTitle(String message) {
        int maxLen = 50;
        if (message.length() <= maxLen) {
            return message;
        }
        return message.substring(0, maxLen) + "...";
    }

    private String loadSystemPrompt() {
        try {
            return systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load system prompt", e);
        }
    }

    private List<Message> buildPromptMessages(String conversationId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, conversationId)
               .orderByAsc(ChatMessage::getCreatedAt);
        List<ChatMessage> savedMessages = chatMessageMapper.selectList(wrapper);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(loadSystemPrompt()));
        for (ChatMessage msg : savedMessages) {
            switch (msg.getRole()) {
                case "user" -> messages.add(new UserMessage(msg.getContent()));
                case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
            }
        }
        return messages;
    }
}
