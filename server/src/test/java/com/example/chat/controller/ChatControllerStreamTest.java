package com.example.chat.controller;

import com.example.chat.service.ChatService;
import com.example.chat.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 流式响应 (SSE) 测试。
 *
 * 验证后端是否正确输出 text/event-stream 格式 (data:内容\n\n)。
 *
 * 运行方式: mvn test -Dtest=ChatControllerStreamTest
 *
 * 如果这些测试通过，说明后端 SSE 输出格式正确，
 * 前端收不到流式内容的问题在前端代码。
 */
@WebMvcTest(
    controllers = ChatController.class,
    excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
    }
)
class ChatControllerStreamTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void chatStream_ShouldReturnSseFormattedResponse() throws Exception {
        SseEmitter emitter = new SseEmitter();

        when(chatService.chatStream(any())).thenReturn(emitter);

        MvcResult result = mockMvc.perform(post("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hello\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        emitter.send("Hello");
        emitter.send(" World");
        emitter.complete();

        String content = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(content.contains("data:Hello"),
                "SSE response should contain 'data:Hello', but got: " + content);
        assertTrue(content.contains("data: World"),
                "SSE response should contain 'data: World', but got: " + content);

        assertTrue(content.lines().anyMatch(line -> line.startsWith("data:")),
                "SSE lines should start with 'data:', but got: " + content);
    }

    @Test
    void chatStream_ShouldReturnContentTypeTextEventStream() throws Exception {
        SseEmitter emitter = new SseEmitter();

        when(chatService.chatStream(any())).thenReturn(emitter);

        MvcResult result = mockMvc.perform(post("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hello\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        emitter.send("test");
        emitter.complete();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    void chatStream_ShouldReturn400ForEmptyMessage() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatStream_ShouldReturn400ForMissingMessage() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
