package com.example.chat.service;

import com.example.chat.dto.ChatRequest;
import com.example.chat.entity.ChatMessage;
import com.example.chat.entity.Conversation;
import com.example.chat.mapper.ChatMessageMapper;
import com.example.chat.mapper.ConversationMapper;
import com.example.chat.service.impl.ChatServiceImpl;
import com.example.chat.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private DeepSeekChatModel chatModel;

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @InjectMocks
    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        UserContext.setUserId(1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ===== chat() 同步聊天 =====

    @Test
    void chat_newConversation_shouldCreateConversationAndReturnResponse() {
        ChatRequest request = new ChatRequest("hello", null);

        when(conversationMapper.selectById(anyString())).thenReturn(null);
        when(conversationMapper.insert(any(Conversation.class))).thenReturn(1);

        when(chatModel.call(any(Prompt.class))).thenReturn(mockAiResponse("Hi there!"));

        var response = chatService.chat(request);

        assertEquals("Hi there!", response.content());
        assertEquals("deepseek-chat", response.model());
        assertNotNull(response.conversationId());

        verify(chatMessageMapper, times(2)).insert(any(ChatMessage.class));
        verify(conversationMapper).insert(any(Conversation.class));
    }

    @Test
    void chat_existingConversation_shouldNotCreateNewConversation() {
        ChatRequest request = new ChatRequest("hello", "existing-id");

        when(conversationMapper.selectById("existing-id")).thenReturn(new Conversation());
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());
        when(chatModel.call(any(Prompt.class))).thenReturn(mockAiResponse("reply"));

        var response = chatService.chat(request);

        assertEquals("existing-id", response.conversationId());
        verify(conversationMapper, never()).insert(any(Conversation.class));
    }

    @Test
    void chat_shouldSaveUserAndAssistantMessages() {
        ChatRequest request = new ChatRequest("hello", null);

        when(conversationMapper.selectById(anyString())).thenReturn(null);
        when(conversationMapper.insert(any(Conversation.class))).thenReturn(1);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockAiResponse("reply"));

        chatService.chat(request);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper, times(2)).insert(captor.capture());

        List<ChatMessage> messages = captor.getAllValues();
        assertEquals("user", messages.get(0).getRole());
        assertEquals("hello", messages.get(0).getContent());
        assertEquals("assistant", messages.get(1).getRole());
        assertEquals("reply", messages.get(1).getContent());
    }

    @Test
    void chat_noCurrentUser_shouldThrow() {
        UserContext.clear();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> chatService.chat(new ChatRequest("hello", null)));
        assertEquals("未登录", ex.getMessage());
    }

    @Test
    void chat_longMessage_shouldTruncateTitle() {
        String longMsg = "a".repeat(100);
        ChatRequest request = new ChatRequest(longMsg, null);

        when(conversationMapper.selectById(anyString())).thenReturn(null);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockAiResponse("ok"));

        chatService.chat(request);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationMapper).insert(captor.capture());

        String title = captor.getValue().getTitle();
        assertTrue(title.endsWith("..."));
        assertEquals(53, title.length());
    }

    // ===== chatStream() 流式聊天 =====

    @Test
    void chatStream_shouldReturnSseEmitter() {
        ChatRequest request = new ChatRequest("hello", null);

        when(conversationMapper.selectById(anyString())).thenReturn(null);
        when(conversationMapper.insert(any(Conversation.class))).thenReturn(1);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.empty());

        SseEmitter emitter = chatService.chatStream(request);

        assertNotNull(emitter);
    }

    @Test
    void chatStream_noCurrentUser_shouldThrow() {
        UserContext.clear();

        assertThrows(IllegalStateException.class,
                () -> chatService.chatStream(new ChatRequest("hello", null)));
    }

    @Test
    void chatStream_shouldBuildPromptWithHistory() {
        ChatRequest request = new ChatRequest("follow-up", "conv-1");

        when(conversationMapper.selectById("conv-1")).thenReturn(new Conversation());
        ChatMessage historyMsg = new ChatMessage("conv-1", "user", "previous");
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(historyMsg));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.empty());

        chatService.chatStream(request);

        verify(chatMessageMapper).insert(argThat(msg ->
                "user".equals(msg.getRole()) && "follow-up".equals(msg.getContent())
        ));
    }

    // ===== listConversations() =====

    @Test
    void listConversations_shouldReturnMappedResponse() {
        Conversation conv = new Conversation();
        conv.setId("id-1");
        conv.setTitle("Test");

        when(conversationMapper.selectList(any())).thenReturn(List.of(conv));

        var result = chatService.listConversations();

        assertEquals(1, result.size());
        assertEquals("id-1", result.get(0).id());
        assertEquals("Test", result.get(0).title());
    }

    // ===== deleteConversation() =====

    @Test
    void deleteConversation_shouldDeleteMessagesThenConversation() {
        when(conversationMapper.selectCount(any())).thenReturn(1L);

        chatService.deleteConversation("conv-1");

        verify(chatMessageMapper).delete(any());
        verify(conversationMapper).deleteById("conv-1");
    }

    @Test
    void deleteConversation_notOwner_shouldThrow() {
        when(conversationMapper.selectCount(any())).thenReturn(0L);

        assertThrows(IllegalArgumentException.class,
                () -> chatService.deleteConversation("conv-1"));

        verify(chatMessageMapper, never()).delete(any());
        verify(conversationMapper, never()).deleteById(anyString());
    }

    // ===== 辅助方法 =====

    private ChatResponse mockAiResponse(String text) {
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        var output = mock(org.springframework.ai.chat.messages.AssistantMessage.class);

        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(output);
        when(output.getText()).thenReturn(text);

        return response;
    }
}
