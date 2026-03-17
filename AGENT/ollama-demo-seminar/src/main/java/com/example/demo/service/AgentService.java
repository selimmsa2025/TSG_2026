package com.example.demo.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class AgentService {

    private final OllamaNativeClient ollamaClient;
    private final List<AgentTool> tools;

    // 간단한 Memory (주의: 현재 싱글톤 상태라 다중 접속 시 섞임)
    private final List<String> memory = new ArrayList<>();
    
    // ⭐ 실행 대기 중인 SQL을 임시로 저장하는 상태 변수
    private String pendingSql = null;

    public AgentService(OllamaNativeClient ollamaClient, List<AgentTool> tools) {
        this.ollamaClient = ollamaClient;
        this.tools = tools;
    }

    public String askAgent(String prompt) {

        // ⭐ 1. 대기 중인 SQL이 있을 때 (버튼 클릭에 대한 시스템 응답 처리)
        if (pendingSql != null) {
            String resultText;
            
            // 프론트엔드 버튼에서 보낸 숨겨진 시스템 명령어 확인
            if (prompt.equals("[SYS_YES]")) {
                resultText = tools.stream()
                        .filter(t -> t.getName().equals("db"))
                        .findFirst()
                        .map(t -> t.execute(pendingSql))
                        .orElse("DB Tool이 없습니다.");
            } else {
                resultText = "작업이 취소되었습니다. 데이터를 변경하지 않았습니다.";
            }
            
            pendingSql = null;
            // 화면에는 예/아니오로 보였지만, 메모리에는 정확히 기록
            memory.add("User: " + (prompt.equals("[SYS_YES]") ? "승인(예)" : "거절(아니오)"));
            memory.add("Result: " + resultText);
            return resultText;
        }

        String context = String.join("\n", memory);
        System.out.println("prompt : " + prompt);

        // ⭐ 2. 자바 기반의 확실한 Rule-based 필터링 강화 (하이브리드 라우팅)
        boolean isDbTask = false;
        String cleanPrompt = prompt.replaceAll(" ", ""); // 공백 제거 후 검사

        // '변경', '바꿔', '업데이트' 등 실무적인 단어 추가
        if (cleanPrompt.contains("조회") || cleanPrompt.contains("테이블") || 
            cleanPrompt.contains("회원") || cleanPrompt.contains("몇명") || 
            cleanPrompt.contains("추가") || cleanPrompt.contains("수정") || 
            cleanPrompt.contains("변경") || cleanPrompt.contains("바꿔") || 
            cleanPrompt.contains("업데이트") || cleanPrompt.contains("DB")) {
            
            isDbTask = true;
            System.out.println("분석된 의도(Rule-based): DB 파이프라인 직행");
            
        } else {
            // ⭐ 3. 괄호 마커[ ]를 강제하는 궁극의 프롬프트
        	String routerPrompt = """
                    [SYSTEM]
                    당신은 시스템의 라우팅을 담당하는 JSON 생성기입니다.
                    이전 대화 맥락과 현재 문장의 의도를 분석하여, 오직 아래의 JSON 형식으로만 응답하세요.
                    마크다운(```json)이나 부연 설명은 절대 금지합니다.
                    
                    [출력 형식]
                    {"intent": "DB"} 또는 {"intent": "CHAT"}
                    
                    [RULES]
                    1. '회원', '사용자', '데이터' 등 시스템 내부 데이터베이스에서 찾아야 하는 정보(조회/수정/추가/삭제)라면 "DB"를 선택하세요.
                    2. 길 찾기, 거리 계산, 날씨, 일반 상식, 단순 인사 등 우리 DB와 무관한 외부 정보 질문은 무조건 "CHAT"을 선택하세요.
                    
                    [판단 예시]
                    Input: 1번 유저 이름이 뭐야?
                    Output: {"intent": "DB"}
                    
                    Input: 세종시에서 대전까지 얼마나 걸려?
                    Output: {"intent": "CHAT"}
                    
                    Input: 오늘 점심 메뉴 추천해줘
                    Output: {"intent": "CHAT"}
                    
                    [이전 대화 맥락]
                    """ + (context.isEmpty() ? "없음" : context) + """
                    
                    [현재 문장]
                    Input: """ + prompt + """
                    \nOutput: """;;
            
        	String responseText = ollamaClient.askOllama(routerPrompt).trim();
            System.out.println("LLM raw routing response: " + responseText);
            
            // 안전장치: 혹시 LLM이 ```json 을 붙이더라도 깔끔하게 제거
            String cleanJson = responseText.replace("```json", "").replace("```", "").replaceAll("\\s+", "");
            
            // ⭐ 정확한 JSON 키-값 매칭
            if (cleanJson.contains("\"intent\":\"DB\"")) {
                isDbTask = true;
                System.out.println("분석된 의도(LLM JSON): DB");
            } else {
                System.out.println("분석된 의도(LLM JSON): CHAT");
            }
        }

        // ⭐ 4. DB 작업 파이프라인 분기
        if (isDbTask) {
            System.out.println("--> DB 파이프라인으로 라우팅됩니다.");

            // LLM에게 SQL 생성 요청
            String sql = ollamaClient.generateSQL(context + "\n사용자 질문: " + prompt);
            System.out.println("LLM raw SQL: " + sql);

            if (sql == null || sql.isEmpty()) {
                return "SQL 생성에 실패했습니다.";
            }

            // LLM이 쿼리를 짜지 못하고 INCOMPLETE를 뱉었을 때의 처리 (안내 멘트 반환)
            if (sql.contains("INCOMPLETE")) {
                String clarification = "명확한 처리를 위해 조금 더 구체적으로 말씀해 주시겠어요?\n(예: '회원 목록 전부 조회해줘', '1번 유저 이름 변경해줘' 등)";

                // 대화 맥락이 끊기지 않도록 메모리에 저장
                memory.add("User: " + prompt);
                memory.add("AI: " + clarification);

                return clarification; // 여기서 바로 종료되어 화면에 안내 멘트가 뜹니다!
            }

            String upperSql = sql.trim().toUpperCase();

            // INSERT나 UPDATE인 경우 프론트엔드에 버튼 띄우기
            if (upperSql.startsWith("INSERT") || upperSql.startsWith("UPDATE")) {
                pendingSql = sql;
                memory.add("User: " + prompt);
                memory.add("AI: 다음 쿼리를 실행할까요?\n" + sql);
                return "[CONFIRM_REQUEST]데이터를 변경하는 작업입니다. 다음 쿼리를 실행할까요?\n\n`" + sql + "`";
            }

            // SELECT 쿼리는 즉시 실행
            String result = tools.stream()
                    .filter(t -> t.getName().equals("db"))
                    .findFirst()
                    .map(t -> t.execute(sql))
                    .orElse("DB Tool이 없습니다.");

            memory.add("User: " + prompt);
            memory.add("SQL: " + sql);
            memory.add("Result: " + result);

            return result;
        }

        // ⭐ 5. 일반 질문 (CHAT 파이프라인)
        System.out.println("--> 일반 대화 파이프라인으로 라우팅됩니다.");
        String response = ollamaClient.askOllama(context + "\n사용자 질문: " + prompt);

        memory.add("User: " + prompt);
        memory.add("AI: " + response);

        return response;
    }
}