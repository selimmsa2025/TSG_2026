package com.example.demo.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WeatherTool {

    @Tool(description = "도시 이름을 입력하면 날씨 정보를 반환한다.")
    public String getWeather(String city) {
    	log.info("=== WeatherTool 호출 ===");
        // 실제 API 대신 하드코딩
        if (city.equals("서울")) {
            return "서울의 현재 날씨는 맑고 15도입니다.";
        }
        return city + "의 날씨 정보가 없습니다.";
    }
}
