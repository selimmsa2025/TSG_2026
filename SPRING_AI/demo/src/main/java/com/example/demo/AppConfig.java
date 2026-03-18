package com.example.demo;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public ChatMemory chatMemory() {
        // 모든 사용자의 대화 기록을 서버 메모리에 관리하는 빈을 생성합니다.
        return new InMemoryChatMemory();
    }
}