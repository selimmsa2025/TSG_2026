package com.example.demo.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor; // M6에서 이 경로로 통합됨
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RagService2 {

    private final ChatClient chatClient;
    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final ChatMemory chatMemory;

    // 상수 에러 방지를 위한 키값 정의
    private static final String CHAT_MEMORY_CONVERSATION_ID = "chat_memory_conversation_id";

    public RagService2(ChatClient.Builder chatClientBuilder, ChatModel chatModel, 
                      VectorStore vectorStore, ChatMemory chatMemory) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.chatMemory = chatMemory;
        
        // 기본 어드바이저 설정
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    // 1. 문서 검색기 생성 (유사도 점수 및 필터 적용)
    private VectorStoreDocumentRetriever createVectorStoreDocumentRetriever(double score, String source) {
        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(score)
                .topK(3)
                .filterExpression(
                    StringUtils.hasText(source) ? 
                    new FilterExpressionBuilder().eq("source", source).build() : null
                )
                .build();
    }

    // 2. 쿼리 변환기 (압축/재작성/번역/확장) 생성 메소드들
    private CompressionQueryTransformer createCompressionQueryTransformer() {
        return CompressionQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(chatModel))
                .build();
    }

    private RewriteQueryTransformer createRewriteQueryTransformer() {
        return RewriteQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(chatModel))
                .build();
    }

    private TranslationQueryTransformer createTranslationQueryTransformer() {
        return TranslationQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(chatModel))
                .targetLanguage("korean")
                .build();
    }

    private MultiQueryExpander createMultiQueryExpander() {
        return MultiQueryExpander.builder()
                .chatClientBuilder(ChatClient.builder(chatModel))
                .includeOriginal(true)
                .numberOfQueries(3)
                .build();
    }

    // -------------------------------------------------------------------------------
    // 3. 대화 메소드 (Chat Methods)

    // 대화 압축 + 메모리 + RAG
    public String chatWithCompression(String question, double score, String source, String conversationId) {
        RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(createCompressionQueryTransformer())
                .documentRetriever(createVectorStoreDocumentRetriever(score, source))
                .build();

        return this.chatClient.prompt()
                .user(question)
                .advisors(
                    new MessageChatMemoryAdvisor(chatMemory), 
                    ragAdvisor
                )
                // CONVERSATION_ID 에러 해결: 문자열 키 직접 사용
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    // 질문 재작성 RAG
    public String chatWithRewriteQuery(String question, double score, String source) {
        RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(createRewriteQueryTransformer())
                .documentRetriever(createVectorStoreDocumentRetriever(score, source))
                .build();

        return this.chatClient.prompt()
                .user(question)
                .advisors(ragAdvisor)
                .call()
                .content();
    }

    // 질문 번역 RAG
    public String chatWithTranslation(String question, double score, String source) {
        RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(createTranslationQueryTransformer())
                .documentRetriever(createVectorStoreDocumentRetriever(score, source))
                .build();

        return this.chatClient.prompt()
                .user(question)
                .advisors(ragAdvisor)
                .call()
                .content();
    }

    // 멀티 쿼리 확장 RAG
    public String chatWithMultiQuery(String question, double score, String source) {
        RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryExpander(createMultiQueryExpander())
                .documentRetriever(createVectorStoreDocumentRetriever(score, source))
                .build();

        return this.chatClient.prompt()
                .user(question)
                .advisors(ragAdvisor)
                .call()
                .content();
    }
}