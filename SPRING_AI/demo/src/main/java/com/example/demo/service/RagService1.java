package com.example.demo.service;

import java.io.IOException;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor; // 패키지 경로 확인
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RagService1 {

	private final ChatClient chatClient;
	private final VectorStore vectorStore;
	private final JdbcTemplate jdbcTemplate;

	// ##### 생성자 (필드 주입보다 생성자 주입 권장) #####
	public RagService1(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
		this.vectorStore = vectorStore;
		this.jdbcTemplate = jdbcTemplate;
		this.chatClient = chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor() // M6에서는 인자 없이 기본 생성 가능
		).build();
	}

	// ##### 벡터 저장소의 데이터를 모두 삭제하는 메소드 #####
	public void clearVectorStore() {
		// 테이블명은 설정에 따라 다를 수 있으니 주의하세요 (기본값: vector_store)
		jdbcTemplate.update("TRUNCATE TABLE vector_store");
	}

	// ##### PDF 파일을 ETL 처리하는 메소드 #####
	public void ragEtl(MultipartFile attach, String source, int chunkSize, int minChunkSizeChars) throws IOException {
		// 1. 추출하기 (M6는 .get() 사용)
		Resource resource = new ByteArrayResource(attach.getBytes());
		PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
		List<Document> documents = reader.get(); // read() -> get()

		// 2. 메타데이터 추가
		documents.forEach(doc -> doc.getMetadata().put("source", source));

		// 3. 변환하기
		TokenTextSplitter transformer = new TokenTextSplitter(chunkSize, minChunkSizeChars, 5, 10000, true);
		List<Document> transformedDocuments = transformer.apply(documents);

		// 4. 적재하기 (add() 또는 accept() 사용)
		vectorStore.accept(transformedDocuments);
		log.info("ETL 완료: {} 개의 청크가 적재되었습니다.", transformedDocuments.size());
	}

	// ##### LLM과 대화하는 메소드 (Advisor 활용) #####
	public String ragChat(String question, double score, String source) {
		// 1. 벡터 저장소 검색 조건 생성
//      이렇게 하면 source가 있을 때와 없을 때를 나눠서 처리할 수 있다.
		SearchRequest.Builder searchRequestBuilder = SearchRequest.builder().query(question).similarityThreshold(score).topK(3);
		if (StringUtils.hasText(source)) {
			searchRequestBuilder.filterExpression("source == '" + source + "'");
		}
		SearchRequest searchRequest = searchRequestBuilder.build();

		List<Document> retrievedDocuments = vectorStore.similaritySearch(searchRequest);

		log.info("RAG 검색 결과 개수 = {}", retrievedDocuments.size());

		for (int i = 0; i < retrievedDocuments.size(); i++) {
			Document doc = retrievedDocuments.get(i);
			log.info("검색 결과 [{}] metadata = {}", i, doc.getMetadata());
			log.info("검색 결과 [{}] content = {}", i, doc.getText());
		}

		// 2. QuestionAnswerAdvisor 설정
		// M6 버전에서는 builder를 통해 더 세밀한 설정이 가능합니다.
		QuestionAnswerAdvisor questionAnswerAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
				.searchRequest(searchRequest).build();

		// 3. 프롬프트를 LLM으로 전송 (Advisors 체이닝)
		return this.chatClient.prompt().user(question).advisors(questionAnswerAdvisor).call().content();
	}
}