package com.tradingagents.chat;

import com.tradingagents.chat.entity.ChatMessage;
import com.tradingagents.chat.entity.ChatSession;
import com.tradingagents.chat.repository.ChatMessageRepository;
import com.tradingagents.chat.repository.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AdvisorService {

    private static final Logger log = LoggerFactory.getLogger(AdvisorService.class);

    private final ChatSessionRepository sessionRepo;
    private final ChatMessageRepository messageRepo;
    private final AdvisorAgent advisorAgent;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AdvisorService(ChatSessionRepository sessionRepo,
                          ChatMessageRepository messageRepo,
                          AdvisorAgent advisorAgent) {
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
        this.advisorAgent = advisorAgent;
    }

    public ChatSession createSession(String title) {
        ChatSession session = new ChatSession();
        session.setTitle(title != null ? title : "新对话");
        return sessionRepo.save(session);
    }

    public List<ChatSession> listSessions() {
        return sessionRepo.findAllByOrderByUpdatedAtDesc();
    }

    public List<ChatMessage> getHistory(Long sessionId) {
        return messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        sessionRepo.deleteById(sessionId);
    }

    /**
     * 处理用户消息并通过 SSE 流式返回 AI 响应
     * 由于 Spring AI 的 ChatClient.prompt().call() 是同步的，
     * 这里先在后台线程执行 LLM 调用，完成后一次性通过 SSE 推送完整响应
     */
    public SseEmitter sendMessage(Long sessionId, String userContent) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        // Save user message
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(userContent);
        messageRepo.save(userMsg);

        // Execute in background thread
        executor.submit(() -> {
            try {
                // Load history (excluding just-saved message)
                List<ChatMessage> allMessages = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
                // Remove the last one (just saved user message) from history
                List<AdvisorAgent.ChatMessagePair> history = allMessages.stream()
                        .filter(m -> !m.getId().equals(userMsg.getId()))
                        .map(m -> new AdvisorAgent.ChatMessagePair(m.getRole(), m.getContent()))
                        .toList();

                // Call advisor agent
                emitter.send(SseEmitter.event().name("start").data("{\"status\":\"thinking\"}"));

                String response = advisorAgent.chat(history, userContent);

                // Save assistant message
                ChatMessage assistantMsg = new ChatMessage();
                assistantMsg.setSessionId(sessionId);
                assistantMsg.setRole("assistant");
                assistantMsg.setContent(response);
                messageRepo.save(assistantMsg);

                // Update session timestamp
                sessionRepo.findById(sessionId).ifPresent(s -> {
                    sessionRepo.save(s); // triggers updatedAt
                });

                // Send response via SSE
                emitter.send(SseEmitter.event().name("message").data(response));
                emitter.send(SseEmitter.event().name("done").data("{\"status\":\"complete\"}"));
                emitter.complete();

            } catch (Exception e) {
                log.error("Chat failed for session {}", sessionId, e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }
}
