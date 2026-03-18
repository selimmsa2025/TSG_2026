package com.example.demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DbTool {

    @Tool(description = "사용자의 요청에 따라 데이터베이스에서 사용자 목록, 사용자 수, 특정 사용자 정보를 조회한다.")
    public String queryData(String query) {
        // TODO: 실제 DB 조회 로직 연결
    	log.info("=== DbTool 호출 ===");
        return "DB 조회 결과 예시: " + query;
    }
}