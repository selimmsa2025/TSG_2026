package com.example.demo.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.demo.agent.RouteType;
import com.example.demo.tool.WeatherTool;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AgentService {

    private final AgentRouterService agentRouterService;
    private final AiServiceByChatClient aiServiceByChatClient;
    private final RagService1 ragService1;
    private final RagService2 ragService2;
    private final ChatClient toolChatClient;

    public AgentService(AgentRouterService agentRouterService,
                        AiServiceByChatClient aiServiceByChatClient,
                        RagService1 ragService1,
                        RagService2 ragService2,
                        ChatClient.Builder builder,
                        WeatherTool weatherTool) {

        this.agentRouterService = agentRouterService;
        this.aiServiceByChatClient = aiServiceByChatClient;
        this.ragService1 = ragService1;
        this.ragService2 = ragService2;

        this.toolChatClient = builder.defaultTools(weatherTool).build();
    }

    public String process(String message, String source, double score) {

        log.info("AgentService.process 시작");
        log.info("사용자 질문 = {}", message);

        // 1️⃣ 최초 라우팅
        RouteType routeType = agentRouterService.route(message);
        log.info("1차 라우팅 결과 = {}", routeType);

        String result = executeByRoute(routeType, message, source, score);

        String evaluation = evaluate(result);

        log.info("평가 결과 = {}", evaluation);

        // 보완 필요 시 재라우팅
        if (evaluation.contains("보완 필요")) {

            log.warn("보완 필요 → 재라우팅 수행");

            RouteType newRoute = agentRouterService.route(message + " 더 정확하게");
            log.info("2차 라우팅 결과 = {}", newRoute);

            result = executeByRoute(newRoute, message, source, score);
        }

        log.info("AgentService.process 종료");

        return result;
    }

    private String executeByRoute(RouteType routeType, String message, String source, double score) {

        switch (routeType) {

            case CHAT:
                log.info("CHAT 실행");
                return handleChat(message);

            case RAG:
                log.info("RAG 실행");
                return handleRag(message, source, score);

            case TOOL:
                log.info("TOOL 실행");
                return handleTool(message);

            default:
                log.warn("알 수 없는 routeType → CHAT fallback");
                return handleChat(message);
        }
    }

    // ##### 평가 (Self-Reflection) #####
    private String evaluate(String result) {

        return aiServiceByChatClient.generateText("""
            너는 AI 응답을 평가하는 검증자다.
            아래 답변이 사용자 질문에 충분한지 판단하라.
            
            부족하면 반드시 "보완 필요"라고 답하고,
            충분하면 "충분"이라고 답하라.

            답변:
            """ + result);
    }

    // ##### 일반 Chat #####
    private String handleChat(String message) {
        log.info("handleChat 실행");
        return aiServiceByChatClient.generateText(message);
    }

    // ##### RAG #####
    private String handleRag(String message, String source, double score) {

        log.info("handleRag 실행");

        String safeSource = StringUtils.hasText(source) ? source : null;

        if (isAdvancedRagQuestion(message)) {
            log.info("고급 RAG(RagService2)");
            return ragService2.chatWithRewriteQuery(message, score, safeSource);
        }

        log.info("기본 RAG(RagService1)");
        return ragService1.ragChat(message, score, safeSource);
    }

    // ##### Tool #####
    private String handleTool(String message) {

        log.info("handleTool 실행");

        return toolChatClient.prompt()
            .system("""
                너는 AI 에이전트다.
                날씨 질문이면 반드시 getWeather 도구를 사용하라.
                일반 질문이면 간단히 답하라.
            """)
            .user(message)
            .call()
            .content();
    }

    // ##### 고급 RAG 판단 #####
    private boolean isAdvancedRagQuestion(String message) {

        if (message == null) return false;

        String text = message.trim();

        return text.contains("다시")
            || text.contains("쉽게")
            || text.contains("요약")
            || text.contains("넓게")
            || text.contains("관련")
            || text.contains("정리");
    }
}