package com.example.chat.controller;

import com.example.chat.dto.*;
import com.example.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ApiResult<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResult.ok(chatService.chat(request));
    }

    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        return chatService.chatStream(request);
    }

    @GetMapping("/conversations")
    public ApiResult<List<ConversationResponse>> listConversations() {
        return ApiResult.ok(chatService.listConversations());
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResult<MessagePageResponse> getMessages(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.ok(chatService.getMessages(id, page, size));
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResult<Void> deleteConversation(@PathVariable String id) {
        chatService.deleteConversation(id);
        return ApiResult.ok();
    }

    @PutMapping("/conversations/{id}/title")
    public ApiResult<ConversationResponse> renameConversation(@PathVariable String id,
                                                                @Valid @RequestBody RenameRequest request) {
        return ApiResult.ok(chatService.renameConversation(id, request.title()));
    }
}
