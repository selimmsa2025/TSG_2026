package com.example.demo.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
public class DbSelectTool implements AgentTool {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DbSelectTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getName() {
        return "db";
    }

    @Override
    public String execute(String sql) {
        
        // 1. SQL 전처리 (앞뒤 공백 제거 및 대문자 변환)
        String cleanSql = sql.trim();
        String upperSql = cleanSql.toUpperCase();

        // 2. 1차 안전장치: SELECT, INSERT, UPDATE만 허용
        if (!upperSql.startsWith("SELECT") && !upperSql.startsWith("INSERT") && !upperSql.startsWith("UPDATE")) {
            System.out.println("차단된 쿼리 (허용되지 않은 DML): " + cleanSql);
            return "보안 경고: 데이터베이스 조회(SELECT), 추가(INSERT), 수정(UPDATE)만 가능합니다.";
        }

        // 3. 2차 안전장치: 치명적인 데이터 파괴 명령어 차단 (INSERT, UPDATE는 목록에서 제거)
        String[] forbiddenKeywords = {
            "DELETE ", "DROP ", "ALTER ", "TRUNCATE ", "GRANT ", "REVOKE ", "COMMIT", "ROLLBACK"
        };
        
        for (String keyword : forbiddenKeywords) {
            if (upperSql.contains(keyword)) {
                System.out.println("차단된 쿼리 (위험 키워드 감지 - " + keyword + "): " + cleanSql);
                return "보안 경고: 허용되지 않은 SQL 키워드(" + keyword.trim() + ")가 감지되었습니다.";
            }
        }

        try {
            // 4. 쿼리 종류에 따른 실행 분기
            if (upperSql.startsWith("SELECT")) {
                // 조회 쿼리는 결과를 JSON 표 형태로 반환
                List<Map<String,Object>> result = jdbcTemplate.queryForList(cleanSql);
                return objectMapper.writeValueAsString(result);
            } else {
                // 추가(INSERT) 및 수정(UPDATE) 쿼리는 적용된 행(Row)의 개수를 반환
                int affectedRows = jdbcTemplate.update(cleanSql);
                return affectedRows + "건의 데이터가 성공적으로 처리(INSERT/UPDATE) 되었습니다.";
            }
            
        } catch (Exception e) {
            return "SQL 실행 오류: " + e.getMessage();
        }
    }
}