package com.tradingagents.chat;

import com.tradingagents.chat.entity.ChatMessage;
import com.tradingagents.chat.entity.ChatSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AdvisorService advisorService;

    public ChatController(AdvisorService advisorService) {
        this.advisorService = advisorService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<ChatSession> createSession(@RequestBody(required = false) Map<String, String> body) {
        String title = body != null ? body.get("title") : null;
        return ResponseEntity.ok(advisorService.createSession(title));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSession>> listSessions() {
        return ResponseEntity.ok(advisorService.listSessions());
    }

    @GetMapping("/sessions/{id}/messages")
    public ResponseEntity<List<ChatMessage>> getHistory(@PathVariable Long id) {
        return ResponseEntity.ok(advisorService.getHistory(id));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable Long id) {
        advisorService.deleteSession(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/sessions/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        return advisorService.sendMessage(id, content);
    }
}
