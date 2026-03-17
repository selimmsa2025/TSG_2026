package com.example.demo.service;

import org.springframework.stereotype.Component;

@Component
public class WeatherTool implements AgentTool {

    @Override
    public String getName() {
        return "weather";
    }

    @Override
    public String execute(String input) {
        return "현재 서울 날씨는 맑음입니다 ☀️";
    }
}