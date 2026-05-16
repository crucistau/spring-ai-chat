package com.example.chat.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.chat.entity.ChatMessage;
import com.example.chat.mapper.ChatMessageMapper;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;

import java.time.LocalDateTime;
import java.util.List;

public class MybatisChatMemoryRepository implements ChatMemoryRepository {

    private final ChatMessageMapper chatMessageMapper;

    public MybatisChatMemoryRepository(ChatMessageMapper chatMessageMapper) {
        this.chatMessageMapper = chatMessageMapper;
    }

    @Override
    public List<String> findConversationIds() {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ChatMessage::getConversationId)
               .groupBy(ChatMessage::getConversationId);
        return chatMessageMapper.selectList(wrapper)
                .stream()
                .map(ChatMessage::getConversationId)
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, conversationId)
               .orderByAsc(ChatMessage::getCreatedAt);
        return chatMessageMapper.selectList(wrapper)
                .stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        LambdaQueryWrapper<ChatMessage> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(ChatMessage::getConversationId, conversationId);
        chatMessageMapper.delete(deleteWrapper);

        for (Message message : messages) {
            chatMessageMapper.insert(toEntity(conversationId, message));
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, conversationId);
        chatMessageMapper.delete(wrapper);
    }

    private Message toMessage(ChatMessage entity) {
        return switch (entity.getRole()) {
            case "user" -> new UserMessage(entity.getContent());
            case "assistant" -> new AssistantMessage(entity.getContent());
            case "system" -> new SystemMessage(entity.getContent());
            default -> throw new IllegalArgumentException("Unknown role: " + entity.getRole());
        };
    }

    private ChatMessage toEntity(String conversationId, Message message) {
        String role = message.getMessageType().name().toLowerCase();
        ChatMessage entity = new ChatMessage(conversationId, role, message.getText());
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }
}
