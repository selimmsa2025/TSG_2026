package com.example.demo.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.AgentService;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequestMapping("/agent")
public class AgentController {

//0309수정
// 컨트롤러 단에서는 직접 tool 포함 ChatClient을 실행하지 않고 service와 분리하여 처리 
// 

	private final AgentService agentService;

	public AgentController(AgentService agentService) {
		this.agentService = agentService;
	}

	@GetMapping(value = "/chat", produces = MediaType.TEXT_PLAIN_VALUE)
	public String chat(@RequestParam("message") String message,
			@RequestParam(value = "source", required = false) String source,
			@RequestParam(value = "score", defaultValue = "0.0") double score) {

		log.info("AgentController 요청 수신");
		log.info("message = {}", message);
		log.info("source = {}", source);
		log.info("score = {}", score);
		return agentService.process(message, source, score);
	}
}