package com.example.rag_spring.controller;

import com.example.rag_spring.service.RagService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    // 스트리밍 채팅
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);

        ragService.stream(request.message())
            .onNext(token -> {
                try {
                    String escaped = token.replace("\n", "\\n");
                    emitter.send(SseEmitter.event().data(escaped));
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            })
            .onComplete(c -> emitter.complete())
            .onError(emitter::completeWithError)
            .start();

        return emitter;
    }

    // 유사도 검색 로그 (사이드 패널용)
    @PostMapping("/search-log")
    public List<Map<String, Object>> searchLog(@RequestBody ChatRequest request) {
        return ragService.searchLog(request.message());
    }

    record ChatRequest(String message) {}
}