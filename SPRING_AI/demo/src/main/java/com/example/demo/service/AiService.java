package com.example.demo.service;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Service
@Slf4j
public class AiService {

    @Autowired
    private ChatModel chatModel;

    // ---------------- 일반 호출 ----------------
    public String generateText(String question) {

        SystemMessage systemMessage =
                new SystemMessage("사용자 질문에 대해 한국어로 답변을 해야 합니다.");

        UserMessage userMessage =
                new UserMessage(question);

        OpenAiChatOptions chatOptions =
                OpenAiChatOptions.builder()
                        .model("gpt-4o-mini")
                        .temperature(0.3)
                        .maxTokens(1000)
                        .build();

        Prompt prompt = new Prompt(
                List.of(systemMessage, userMessage),
                chatOptions
        );

        ChatResponse response = chatModel.call(prompt);

        AssistantMessage assistantMessage =
                response.getResult().getOutput();

        return assistantMessage.getText();
    }

    // ---------------- 스트리밍 ----------------
    public Flux<String> generateStreamText(String question) {

        SystemMessage systemMessage =
                new SystemMessage("사용자 질문에 대해 한국어로 답변을 해야 합니다.");

        UserMessage userMessage =
                new UserMessage(question);

        OpenAiChatOptions chatOptions =
                OpenAiChatOptions.builder()
                        .model("gpt-4o-mini")
                        .temperature(0.3)
                        .maxTokens(1000)
                        .build();

        Prompt prompt = new Prompt(
                List.of(systemMessage, userMessage),
                chatOptions
        );

        return chatModel.stream(prompt)
                .map(chatResponse -> {
                    AssistantMessage message =
                            chatResponse.getResult().getOutput();
                    return message.getText() == null ? "" : message.getText();
                });
    }
    
}