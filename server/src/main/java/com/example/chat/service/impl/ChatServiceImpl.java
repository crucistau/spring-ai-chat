package com.example.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.chat.dto.*;
import com.example.chat.entity.ChatMessage;
import com.example.chat.entity.Conversation;
import com.example.chat.mapper.ChatMessageMapper;
import com.example.chat.mapper.ConversationMapper;
import com.example.chat.service.ChatService;
import com.example.chat.util.UserContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final ConversationMapper conversationMapper;
    private final ChatMessageMapper chatMessageMapper;

    public ChatServiceImpl(ChatClient chatClient,
                            ChatMemory chatMemory,
                            ConversationMapper conversationMapper,
                            ChatMessageMapper chatMessageMapper) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.conversationMapper = conversationMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        Long userId = requireCurrentUser();
        String conversationId = resolveConversationId(request.conversationId());
        ensureConversationExists(userId, conversationId, request.message());

        String content = chatClient.prompt()
                .user(request.message())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        return ChatResponse.of(content, conversationId, "deepseek-chat");
    }

    @Override
    public SseEmitter chatStream(ChatRequest request) {
        Long userId = requireCurrentUser();
        String conversationId = resolveConversationId(request.conversationId());
        ensureConversationExists(userId, conversationId, request.message());

        SseEmitter emitter = new SseEmitter(0L);

        chatClient.prompt()
                .user(request.message())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
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
    public MessagePageResponse getMessages(String conversationId, int page, int size) {
        Long userId = requireCurrentUser();
        verifyOwnership(userId, conversationId);

        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, conversationId)
               .orderByDesc(ChatMessage::getCreatedAt);

        Page<ChatMessage> pageResult = chatMessageMapper.selectPage(new Page<>(page, size), wrapper);

        List<ChatMessageResponse> messages = pageResult.getRecords()
                .stream()
                .map(m -> new ChatMessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();

        List<ChatMessageResponse> chronological = new java.util.ArrayList<>(messages);
        Collections.reverse(chronological);

        return new MessagePageResponse(chronological, page < pageResult.getPages(), pageResult.getTotal());
    }

    @Override
    public void deleteConversation(String conversationId) {
        Long userId = requireCurrentUser();
        verifyOwnership(userId, conversationId);

        chatMemory.clear(conversationId);
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
}
