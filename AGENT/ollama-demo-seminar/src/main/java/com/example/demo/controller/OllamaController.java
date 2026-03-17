package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.AgentService;

@RestController
public class OllamaController {

    private final AgentService agentService;

    public OllamaController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/ask")
    public String ask(@RequestParam(name = "prompt") String prompt) {
        return agentService.askAgent(prompt);
    }
}