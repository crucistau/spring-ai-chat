package com.example.chat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.chat.dto.*;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
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
    private ChatMemory chatMemory;

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private ChatMessageMapper chatMessageMapper;

    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        UserContext.setUserId(1L);
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        chatService = new ChatServiceImpl(chatClient, chatMemory, conversationMapper, chatMessageMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ===== chat() =====

    @Test
    void chat_newConversation_shouldCreateConversationAndReturnResponse() {
        ChatRequest request = new ChatRequest("hello", null);

        when(conversationMapper.selectById(anyString())).thenReturn(null);
        when(conversationMapper.insert(any(Conversation.class))).thenReturn(1);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockAiResponse("Hi there!"));

        var response = chatService.chat(request);

        assertEquals("Hi there!", response.content());
        assertNotNull(response.conversationId());
        verify(conversationMapper).insert(any(Conversation.class));
    }

    @Test
    void chat_existingConversation_shouldNotCreateNewConversation() {
        ChatRequest request = new ChatRequest("hello", "existing-id");

        when(conversationMapper.selectById("existing-id")).thenReturn(new Conversation());
        when(chatModel.call(any(Prompt.class))).thenReturn(mockAiResponse("reply"));

        var response = chatService.chat(request);

        assertEquals("existing-id", response.conversationId());
        verify(conversationMapper, never()).insert(any(Conversation.class));
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

    // ===== chatStream() =====

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

    // ===== getMessages() =====

    @Test
    void getMessages_shouldReturnPaginatedResponse() {
        ChatMessage msg = new ChatMessage();
        msg.setId(1L);
        msg.setRole("user");
        msg.setContent("hello");
        msg.setConversationId("conv-1");

        Page<ChatMessage> pageResult = new Page<>(1, 20);
        pageResult.setRecords(List.of(msg));
        pageResult.setTotal(1);
        pageResult.setPages(1);

        when(conversationMapper.selectCount(any())).thenReturn(1L);
        when(chatMessageMapper.selectPage(any(Page.class), any())).thenReturn(pageResult);

        var result = chatService.getMessages("conv-1", 1, 20);

        assertEquals(1, result.messages().size());
        assertEquals("hello", result.messages().get(0).content());
        assertFalse(result.hasMore());
        assertEquals(1, result.total());
    }

    @Test
    void getMessages_hasMorePages_shouldReturnTrue() {
        ChatMessage msg = new ChatMessage();
        msg.setId(1L);
        msg.setRole("user");
        msg.setContent("hello");

        Page<ChatMessage> pageResult = new Page<>(1, 20);
        pageResult.setRecords(List.of(msg));
        pageResult.setTotal(50);
        pageResult.setPages(3);

        when(conversationMapper.selectCount(any())).thenReturn(1L);
        when(chatMessageMapper.selectPage(any(Page.class), any())).thenReturn(pageResult);

        var result = chatService.getMessages("conv-1", 1, 20);

        assertTrue(result.hasMore());
        assertEquals(50, result.total());
    }

    // ===== deleteConversation() =====

    @Test
    void deleteConversation_shouldClearMemoryAndDeleteConversation() {
        when(conversationMapper.selectCount(any())).thenReturn(1L);

        chatService.deleteConversation("conv-1");

        verify(chatMemory).clear("conv-1");
        verify(conversationMapper).deleteById("conv-1");
    }

    @Test
    void deleteConversation_notOwner_shouldThrow() {
        when(conversationMapper.selectCount(any())).thenReturn(0L);

        assertThrows(IllegalArgumentException.class,
                () -> chatService.deleteConversation("conv-1"));

        verify(chatMemory, never()).clear(anyString());
        verify(conversationMapper, never()).deleteById(anyString());
    }

    // ===== helpers =====

    private ChatResponse mockAiResponse(String text) {
        AssistantMessage message = new AssistantMessage(text);
        Generation generation = new Generation(message);
        return new ChatResponse(List.of(generation));
    }
}
