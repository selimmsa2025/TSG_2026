package com.example.demo.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonMetadataGenerator;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.KeywordMetadataEnricher;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ETLService {

    private final ChatModel chatModel;
    private final VectorStore vectorStore;

    public ETLService(ChatModel chatModel, VectorStore vectorStore) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
    }

    // ================= 파일 ETL =================
    public String etlFromFile(String title, String author, MultipartFile attach) throws IOException {
        List<Document> documents = extractFromFile(attach);

        if (documents == null || documents.isEmpty()) {
            return ".txt, .pdf, .doc, .docx 파일 중 하나를 업로드하세요.";
        }

        log.info("추출된 원본 Document 수: {} 개", documents.size());

        // 메타데이터 주입
        documents.forEach(doc -> {
            doc.getMetadata().put("title", title);
            doc.getMetadata().put("author", author);
            doc.getMetadata().put("source", attach.getOriginalFilename());
        });

        // 데이터 변환 (분할 + 키워드 추출)
        List<Document> transformed = transform(documents);
        
        log.info("변환 완료 (Chunking/Enrichment). 최종 Document 수: {} 개", transformed.size());

        // 벡터 저장소 저장 (1.0.0-M6 표준)
        vectorStore.accept(transformed);

        return "문서 ETL 완료";
    }

    // ================= 파일 추출 (1.0.0-M6 .get() 메서드 적용) =================
    private List<Document> extractFromFile(MultipartFile attach) throws IOException {
        Resource resource = new ByteArrayResource(attach.getBytes());
        String contentType = attach.getContentType();

        if ("text/plain".equals(contentType)) {
            return new TextReader(resource).get();
        }

        if ("application/pdf".equals(contentType)) {
            return new PagePdfDocumentReader(resource).get();
        }

        // Tika는 Word, PPT 등 다양한 오피스 파일 지원
        if (contentType != null && (contentType.contains("word") || contentType.contains("officedocument"))) {
            return new TikaDocumentReader(resource).get();
        }

        return null;
    }

    // ================= 데이터 변환 (Transform) =================
    private List<Document> transform(List<Document> documents) {
        // 1. 텍스트 분할 (청킹)
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> splitDocs = splitter.apply(documents);

        // 2. 키워드 추출 (AI 모델 활용)
        // 주의: 이 작업은 OpenAI API 호출이 발생하여 비용과 시간이 소모됩니다.
        try {
            KeywordMetadataEnricher enricher = new KeywordMetadataEnricher(chatModel, 5);
            return enricher.apply(splitDocs);
        } catch (Exception e) {
            log.warn("키워드 추출 실패 (건너뜀): {}", e.getMessage());
            return splitDocs; // 실패 시 분할된 문서만 반환
        }
    }

    // ================= HTML ETL (Jsoup 직접 활용) =================
    public String etlFromHtml(String title, String author, String url) throws Exception {
        log.info("HTML 크롤링 시작: {}", url);

        // JsoupDocumentReader 대신 직접 Jsoup 사용하여 텍스트 추출
        String webText = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get()
                .body()
                .text();

        // 추출된 텍스트로 Document 생성
        Document doc = new Document(webText);
        doc.getMetadata().putAll(Map.of(
                "title", title,
                "author", author,
                "url", url,
                "source", "web_crawler"
        ));

        // 변환 및 저장
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> transformed = splitter.apply(List.of(doc));
        vectorStore.accept(transformed);

        return "HTML ETL 완료";
    }

    // ================= JSON ETL (1.0.0-M6 스타일) =================
    public String etlFromJson(String url) throws Exception {
        Resource resource = new UrlResource(url);

        // JsonReader 설정
        JsonReader reader = new JsonReader(resource, new JsonMetadataGenerator() {
            @Override
            public Map<String, Object> generate(Map<String, Object> jsonMap) {
                return Map.of("source_url", url);
            }
        }, "content"); // JSON 필드 중 실제 본문 데이터가 담긴 Key 입력

        List<Document> documents = reader.get();

        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> transformed = splitter.apply(documents);

        vectorStore.accept(transformed);

        return "JSON ETL 완료";
    }
}