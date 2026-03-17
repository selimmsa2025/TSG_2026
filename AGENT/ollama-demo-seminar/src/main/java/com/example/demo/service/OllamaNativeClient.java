package com.example.demo.service;

import org.springframework.stereotype.Service;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class OllamaNativeClient {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@JsonIgnoreProperties(ignoreUnknown = true)
	record OllamaResponse(String response) {
	}

	/**
	 * 일반 LLM 질문
	 */
	public String askOllama(String promptText) {
	    try {

	        ObjectMapper mapper = new ObjectMapper();

	        // JSON 객체 생성
	        var body = mapper.createObjectNode();
	        body.put("model", "llama3.1");
	        body.put("prompt", promptText);
	        body.put("stream", false);

	        String jsonPayload = mapper.writeValueAsString(body);

	        HttpRequest request = HttpRequest.newBuilder()
	                .uri(URI.create("http://localhost:11434/api/generate"))
	                .header("Content-Type", "application/json")
	                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
	                .build();

	        HttpClient client = HttpClient.newHttpClient();

	        HttpResponse<String> response =
	                client.send(request, HttpResponse.BodyHandlers.ofString());

	        System.out.println("Ollama raw response: " + response.body());

	        return mapper
	                .readTree(response.body())
	                .get("response")
	                .asText();

	    } catch (Exception e) {
	        return "통신 에러 발생: " + e.getMessage();
	    }
	}

	/**
	 * 자연어 질문 → SQL 생성
	 */
	/**
	 * 자연어 질문 → SQL 생성
	 */
	public String generateSQL(String question) {

		String prompt = """
				당신은 PostgreSQL 데이터베이스 전문가입니다.
				사용자의 요청을 분석하여 완벽한 실행 가능한 SQL(SELECT, INSERT, UPDATE)을 생성하세요.

				[중요 규칙]
				1. 절대로 다른 설명이나 인사말, 마크다운(```sql 등)을 쓰지 마세요.
				2. 테이블명 앞에 반드시 usr 스키마를 붙여서 작성하세요. ex) usr.usr_user
				3. ⭐만약 사용자의 요청이 너무 모호해서(예: "DB 작업하자", "조회해줘") 구체적인 SQL을 짤 수 없다면, 임의로 쿼리를 만들지 말고 오직 'INCOMPLETE' 라는 단어만 출력하세요.⭐
				4. 구체적인 요청일 경우에만 오직 SQL 문장 단 하나만 텍스트로 출력하세요.
				
				[테이블 정보]
				usr.usr_user(user_id, login_id, password, user_name, email, role_cd, use_yn, reg_dt, mod_dt)

				요청:
				""" + question;
				
		String response = askOllama(prompt);

		System.out.println("LLM raw response: " + response);

		// 혹시라도 LLM이 마크다운이나 앞뒤 공백을 붙일 경우를 대비한 강력한 클렌징
		String sql = response.replace("SQL:", "")
                             .replace("```sql", "")
                             .replace("```", "")
                             .replaceAll("^\"|\"$", "")
                             .trim();

		return sql;
	}
}
