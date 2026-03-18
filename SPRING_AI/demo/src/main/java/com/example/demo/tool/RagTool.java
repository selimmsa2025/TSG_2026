package com.example.demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.example.demo.service.RagService1;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RagTool {

    private final RagService1 ragService1;

    public RagTool(RagService1 ragService1) {
        this.ragService1 = ragService1;
    }

    @Tool(description = "문서, 파일, 자료에서 관련 내용을 검색하고 그 내용을 바탕으로 답변한다.")
    public String searchDocuments(String question) {

        log.info("=== RagTool 호출 시작 ===");
        log.info("RagTool 입력 question = {}", question);

        // source, score는 지금은 고정값으로 사용
        String result = ragService1.ragChat(question, 0.0, null);

        log.info("RagTool 반환 result = {}", result);
        log.info("=== RagTool 호출 종료 ===");

        return result;
    }
}