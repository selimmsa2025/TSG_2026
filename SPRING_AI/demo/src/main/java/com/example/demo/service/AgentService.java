package com.example.demo.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import com.example.demo.tool.RagTool;
import com.example.demo.tool.DbTool;
import com.example.demo.tool.WeatherTool;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AgentService {

	private final ChatClient agentChatClient;

	public AgentService(ChatClient.Builder builder, RagTool ragTool, WeatherTool weatherTool, DbTool dbTool) {

		this.agentChatClient = builder.defaultTools(ragTool, weatherTool, dbTool)
				.build();
	}

	public String process(String message) {

		log.info("AgentService.process 시작");
		log.info("사용자 질문 = {}", message);
		try {

			String result = agentChatClient.prompt().system("""
										          너는 사용자의 요청을 해결하는 AI 에이전트다.
					항상 먼저 스스로 해결할 수 있는지 판단하고, 필요할 때만 도구를 사용한다.

					동작 원칙:
					- 사용자의 질문을 먼저 이해하고, 일반적인 지식으로 답할 수 있는지 판단하라.
					- 일반적인 개념 설명, 상식, 기술 설명, 대화는 도구를 사용하지 말고 직접 답하라.
					- 문서, 파일, 업로드된 자료, 특정 데이터 조회 등 외부 정보가 필요한 경우에만 도구를 사용하라.
					- 하나의 도구로 부족하면 여러 도구를 순차적으로 사용할 수 있다.
					- 도구 사용이 반드시 필요한 경우에만 선택적으로 사용하라.
					- 도구 없이 충분히 답할 수 있다면 절대 도구를 사용하지 마라.

					도구 사용 기준:
					- RagTool: 문서, 파일, 자료, PDF, 업로드된 내용 등 특정 자료 기반 질문일 때만 사용
					- DbTool: 사용자 목록, 사용자 정보, 개수, 특정 데이터 조회 요청일 때 사용
					- WeatherTool: 날씨, 기온, 지역 날씨 정보 요청일 때 사용

					응답 원칙:
					- 내부 판단 과정은 드러내지 말고 결과만 간결하게 제공하라.
					- 도구 결과가 있다면 자연스럽게 정리해서 전달하라.
					- 불필요한 도구 사용을 피하고, 가장 단순한 방법으로 답하라.
					- 최종 답변은 사용자가 바로 이해하고 활용할 수 있게 명확하게 작성하라.

										                """).user(message).call().content();
			log.info("LLM 호출 완료");
			log.info("최종 응답 = {}", result);

			return result;
		} catch (Exception e) {
			log.error("AgentService.process 중 오류 발생", e);
			throw e;
		} finally {
			long endTime = System.currentTimeMillis();
		}
	}
}
