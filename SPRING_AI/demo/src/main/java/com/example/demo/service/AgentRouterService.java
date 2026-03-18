package com.example.demo.service;

import org.springframework.ai.chat.client.ChatClient;

import org.springframework.stereotype.Service;

import com.example.demo.agent.RouteType;
import lombok.extern.slf4j.Slf4j;

//0309추가
// 사용자 질문을 분석하여 일반 생성형 응답(chat), 문서 검색 기반(rag), 도구 호출(tool) 중 어떤 처리를 할지 결정한는 라우터 

@Service
@Slf4j
public class AgentRouterService {

	private final ChatClient chatClient; // llm에게 질문을 보내야함 (질문 분류하기 위해서)

	// ChatClient.Builder를 받아서 기본 chatClient를 생성
	public AgentRouterService(ChatClient.Builder builder) {
		this.chatClient = builder.build();
	}

	// 정의해둔 RouteType 반환
	public RouteType route(String question) {
		log.info("AgentRouterService.route 시작");

		String result = chatClient.prompt().system("""
				    너는 사용자 질문을 분류하는 AI 라우터다.
				    아래 세 가지 분류 중 하나만 선택해서 반환하라.

				    CHAT:
				    - 일반 지식 질문
				    - 설명 요청
				    - 개념 질문
				    - 문서 검색이나 외부 도구 사용이 필요 없는 질문

				    RAG:
				    - 문서, PDF, 업로드 파일, 조항, source, 자료 내용 관련 질문
				    - 벡터 검색이나 문서 검색이 필요한 질문

				    TOOL:
				    - 날씨, 시간, 계산 등 외부 도구 사용이 필요한 질문

				    반드시 CHAT, RAG, TOOL 중 하나만 출력하라.
				    다른 설명은 절대 하지 마라.
				""").user(question).call().content();
		
		log.info("LLM 분류 결과 = {}", result);
		String normalized = result == null ? "" : result.trim().toUpperCase();

		if ("RAG".equals(normalized)) {
			return RouteType.RAG;
		}

		if ("TOOL".equals(normalized)) {
			return RouteType.TOOL;
		}

		return RouteType.CHAT;
	}
}