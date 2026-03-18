package com.example.rag_spring.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagService {

    // ═══════════════════════════════════════════════════════
    // [6단계] Prompt - AI 역할 정의 (시스템 메시지)
    //
    // LangChain4j의 AiServices는 인터페이스를 기반으로
    // 구현체를 런타임에 자동 생성함 (실제 코드 작성 불필요)
    // @SystemMessage: 모든 대화 앞에 자동으로 삽입되는 지시문
    // ═══════════════════════════════════════════════════════
    interface RagAssistant {
        @SystemMessage("""
                당신은 회사 안내 AI입니다.
                제공된 문서를 기반으로만 답변하세요.
                문서에 없는 내용은 "해당 정보를 찾을 수 없습니다"라고 답하세요.
                항상 한국어로 답변하세요.
                """)
        // TokenStream: 응답을 토큰 단위로 스트리밍하는 반환 타입
        // (일반 String 반환 시 응답 완성 후 한번에 전달)
        TokenStream chat(String userMessage);
    }

    // ═══════════════════════════════════════════════════════
    // application.properties 에서 값을 읽어 자동 주입
    // ═══════════════════════════════════════════════════════
    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.chat-model}")
    private String chatModelName;           // 사용할 LLM 모델 (ex. gpt-4o-mini)

    @Value("${openai.embedding-model}")
    private String embeddingModelName;      // 사용할 임베딩 모델 (ex. text-embedding-3-small)

    @Value("${rag.chunk-size}")
    private int chunkSize;                  // 청크 최대 토큰 수 (ex. 500)

    @Value("${rag.chunk-overlap}")
    private int chunkOverlap;              	// 청크 간 겹치는 토큰 수 (ex. 50)

    @Value("${rag.max-results}")
    private int maxResults;                	// 유사도 검색 시 반환할 청크 수 (ex. 5)

    @Value("${rag.min-score}")
    private double minScore;               	// 유사도 최소 임계값 (ex. 0.3, 코사인 유사도 기준)

    // 8단계에서 조립된 RAG 체인을 담는 변수
    // 질문이 들어올 때마다 이 assistant를 통해 체인 실행
    private RagAssistant assistant;
    private EmbeddingModel embeddingModel;
    private InMemoryEmbeddingStore<TextSegment> embeddingStore;

    // 벡터 앞 5개 미리보기
    private String vectorPreview(float[] v) {
        return String.format("[%.4f, %.4f, %.4f, %.4f, %.4f, ...]",
                v[0], v[1], v[2], v[3], v[4]);
    }

    // ═══════════════════════════════════════════════════════
    // @PostConstruct: 스프링이 Bean 생성 직후 자동으로 딱 한 번 실행
    // → 서버 시작 시 RAG 파이프라인을 미리 준비 (Pre-processing)
    // ═══════════════════════════════════════════════════════
    @PostConstruct
    public void init() throws Exception {
        System.out.println("\n=== RAG 시스템 초기화 중... ===\n");

        // ───────────────────────────────────────────────────
        // [1단계] Document Load
        // resources/documents 폴더의 PDF, TXT 파일을 읽어
        // LangChain4j의 Document 객체 리스트로 변환
        // ───────────────────────────────────────────────────
        System.out.println("[1단계] Document Load - 문서 로딩...");
        var documents = new ArrayList<Document>();

        // getClassLoader().getResource(): classpath 기준으로 경로를 찾음
        // → src/main/resources/documents 폴더를 가리킴
        URL resourceUrl = getClass().getClassLoader().getResource("documents");
        Path documentPath = Paths.get(resourceUrl.toURI());

        // Files.walk(): 폴더 안의 모든 파일을 재귀적으로 탐색
        // try-with-resources: 탐색 완료 후 스트림 자동 close
        try (var paths = Files.walk(documentPath)) {
            paths.filter(Files::isRegularFile)  // 폴더 제외, 파일만 필터링
                 .forEach(p -> {
                     String fileName = p.toString().toLowerCase();
                     try {
                         if (fileName.endsWith(".pdf")) {
                             documents.add(FileSystemDocumentLoader.loadDocument(
                                 p, new ApachePdfBoxDocumentParser()));
                             System.out.println("  → PDF 로드: " + p.getFileName());
                         } else if (fileName.endsWith(".txt")) {
                             documents.add(FileSystemDocumentLoader.loadDocument(
                                 p, new TextDocumentParser()));
                             System.out.println("  → TXT 로드: " + p.getFileName());
                         }
                         // 로드된 Document에는 텍스트 내용 + 파일명 등 메타데이터가 자동 포함됨
                     } catch (Exception e) {
                         System.out.println("  ⚠️ 실패: " + p.getFileName());
                     }
                 });
        }
        System.out.println("  → 총 " + documents.size() + "개 문서 로드 완료\n");

        // ───────────────────────────────────────────────────
        // [2단계] Text Split
        // recursive: 문단 → 문장 → 단어 순으로 자연스러운 경계를 찾아 분할
        // chunkOverlap: 앞뒤 청크가 일부 겹치게 해서 문맥 단절 방지
        // ───────────────────────────────────────────────────
        System.out.println("[2단계] Text Split - 청크 분할...");
        DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
        System.out.println("  → chunk size: " + chunkSize + ", overlap: " + chunkOverlap);

        // 첫 번째 문서의 청크 3개 미리보기
        System.out.println("\n  ── 청크 미리보기 (첫 번째 문서, 처음 3개) ──");
        if (!documents.isEmpty()) {
            var firstDoc = documents.get(0);
            var previewChunks = splitter.split(firstDoc);
            System.out.println("  📄 " + firstDoc.metadata().getString("file_name")
                    + " → 총 " + previewChunks.size() + "개 청크");
            for (int i = 0; i < Math.min(3, previewChunks.size()); i++) {
                String text = previewChunks.get(i).text().replaceAll("\\s+", " ");
                System.out.printf("  [청크 %d] %s%n", i + 1,
                        text.length() > 100 ? text.substring(0, 100) + "..." : text);
            }
        }
        System.out.println("  ──────────────────────────────────────────\n");

        // ───────────────────────────────────────────────────
        // [3단계] Embedding
        // 텍스트의 의미를 1536차원 숫자 배열로 표현
        // 의미가 비슷한 텍스트 → 벡터 공간에서 가까운 위치
        // ───────────────────────────────────────────────────
        System.out.println("[3단계] Embedding - 임베딩 모델 로딩...");
        embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(embeddingModelName)  // text-embedding-3-small
                .build();
        System.out.println("  → 모델: " + embeddingModelName);

        // 청크 → 벡터 변환 3개 미리보기
        System.out.println("\n  ── 임베딩 변환 미리보기 (처음 3개) ──");
        if (!documents.isEmpty()) {
            var previewChunks = splitter.split(documents.get(0));
            for (int i = 0; i < Math.min(3, previewChunks.size()); i++) {
                float[] v = embeddingModel.embed(previewChunks.get(i).text()).content().vector();
                System.out.printf("  [청크 %d] → [벡터 %d] %d차원 | %s%n",
                        i + 1, i + 1, v.length, vectorPreview(v));
            }
        }
        System.out.println("  ──────────────────────────────────────────\n");

        // ───────────────────────────────────────────────────
        // [4단계] Vector DB
        // InMemoryEmbeddingStore: 벡터를 서버 메모리에 저장 (재시작 시 초기화)
        // EmbeddingStoreIngestor: 2~4단계(분할→임베딩→저장)를 한번에 처리하는 파이프라인
        // ───────────────────────────────────────────────────
        System.out.println("[4단계] Vector DB - 벡터 저장소 생성 및 저장...");
        embeddingStore = new InMemoryEmbeddingStore<>();
        EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build()
                .ingest(documents);
        System.out.println("  → InMemoryEmbeddingStore 저장 완료\n");

        // ───────────────────────────────────────────────────
        // [5단계] Retriever
        // 질문이 들어오면:
        // 1. 질문 텍스트를 임베딩 모델로 벡터 변환
        // 2. 저장된 청크 벡터들과 코사인 유사도 계산
        // 3. 유사도 높은 상위 N개 청크 반환
        // ───────────────────────────────────────────────────
        System.out.println("[5단계] Retriever - 검색기 구성...");
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(maxResults)  // 상위 N개 청크 반환
                .minScore(minScore)      // 이 유사도 미만은 버림
                .build();
        System.out.println("  → 코사인 유사도 | maxResults: " + maxResults + ", minScore: " + minScore + "\n");

        // ───────────────────────────────────────────────────
        // [7단계] LLM
        // OpenAiStreamingChatModel: 응답을 토큰 단위로 스트리밍
        // (일반 OpenAiChatModel은 응답 완성 후 한번에 반환)
        // temperature: 0에 가까울수록 일관된 답변, 1에 가까울수록 창의적 답변
        // ───────────────────────────────────────────────────
        System.out.println("[7단계] LLM - OpenAI 스트리밍 모델 연결...");
        OpenAiStreamingChatModel streamingChatModel = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(chatModelName)
                .temperature(0.3)
                .build();
        System.out.println("  → 모델: " + chatModelName + "\n");

        // ───────────────────────────────────────────────────
        // [8단계] Chain
        // AiServices.builder(): RagAssistant 인터페이스의 구현체를 런타임에 자동 생성
        // → assistant.chat() 호출 한 번으로 아래 과정이 자동 실행:
        //   1. contentRetriever로 관련 청크 검색 (5단계)
        //   2. SystemMessage + 검색된 청크 + 질문 조합 (6단계)
        //   3. streamingChatModel로 LLM 호출 (7단계)
        // chatMemory: 최근 N개 대화를 기억해 문맥 있는 대화 가능
        // ───────────────────────────────────────────────────
        System.out.println("[8단계] Chain - RAG 파이프라인 조립...");
        this.assistant = AiServices.builder(RagAssistant.class)
                .streamingChatLanguageModel(streamingChatModel)
                .contentRetriever(contentRetriever)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
        System.out.println("  → AiServices 조립 완료\n");
        System.out.println("=== RAG 초기화 완료! ===\n");
    }

    // ═══════════════════════════════════════════════════════
    // 질문을 받아 8단계에서 조립된 RAG 체인을 실행
    // 반환된 TokenStream을 ChatController가 SSE로 브라우저에 전달
    // ═══════════════════════════════════════════════════════
    public TokenStream stream(String message) {

        // 유사도 검색으로 출처 파일명 추출 (1위 청크 기준)
        Embedding questionEmbedding = embeddingModel.embed(message).content();
        var searchResult = embeddingStore.search(
            EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build()
        );

        // 1위 청크의 파일명을 출처로 사용
        String sourceText = "알 수 없음";
        if (!searchResult.matches().isEmpty()) {
            String fileName = searchResult.matches().get(0)
                    .embedded().metadata().getString("file_name");
            if (fileName != null) sourceText = fileName;
        }

        // 출처를 질문 뒤에 붙여서 LLM에 전달
        String messageWithSource = message + "\n\n[답변 마지막에 줄바꿈 후 반드시 이렇게 출처를 표시하세요: 📄 출처: " + sourceText + "]";

        return assistant.chat(messageWithSource);
    }

    // ═══════════════════════════════════════════════════════
    // 질문에 대한 유사도 검색 결과 반환
    // ChatController가 사이드 패널에 표시하기 위해 호출
    // ═══════════════════════════════════════════════════════
    public List<Map<String, Object>> searchLog(String question) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            // 질문 벡터화
            Embedding questionEmbedding = embeddingModel.embed(question).content();
            float[] qv = questionEmbedding.vector();

            // 질문 벡터 정보
            Map<String, Object> questionInfo = new LinkedHashMap<>();
            questionInfo.put("type", "question");
            questionInfo.put("text", question);
            questionInfo.put("dimension", qv.length);
            questionInfo.put("vector", vectorPreview(qv));
            result.add(questionInfo);

            // 유사도 검색
            var searchResult = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                    .queryEmbedding(questionEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build()
            );

            // 검색 결과 (순위, 유사도, 벡터, 출처, 내용)
            int rank = 1;
            for (var match : searchResult.matches()) {
                String text = match.embedded().text().replaceAll("\\s+", " ");
                float[] cv = embeddingModel.embed(match.embedded().text()).content().vector();

                String fileName = match.embedded().metadata().getString("file_name");
                if (fileName == null) fileName = "알 수 없음";

                Map<String, Object> chunkInfo = new LinkedHashMap<>();
                chunkInfo.put("type", "match");
                chunkInfo.put("rank", rank++);
                chunkInfo.put("score", String.format("%.4f", match.score()));
                chunkInfo.put("vector", vectorPreview(cv));
                chunkInfo.put("source", fileName);
                chunkInfo.put("text", text.length() > 120 ? text.substring(0, 120) + "..." : text);
                result.add(chunkInfo);
            }

            if (searchResult.matches().isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("type", "empty");
                empty.put("text", "유사도 " + minScore + " 이상의 결과 없음");
                result.add(empty);
            }

        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("type", "error");
            error.put("text", e.getMessage());
            result.add(error);
        }
        return result;
    }
}