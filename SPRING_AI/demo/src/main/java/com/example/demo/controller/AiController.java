package com.example.demo.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.service.AiService;
import com.example.demo.service.AiServiceByChatClient;
import com.example.demo.service.ETLService;
import com.example.demo.service.RagService1;
import com.example.demo.service.RagService2;

import jakarta.servlet.http.HttpSession;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
public class AiController {

    private final ChatClient chatClient;
    @Autowired 
    private ETLService etlService;

    @Autowired 
    private RagService1 ragService1;

     @Autowired 
    private RagService2 ragService2; 

    //chatclient.builder를 주입받아 생성
    public AiController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/ask")
    public String ask(@RequestParam("message") String message) {
        return chatClient.prompt()
                .system("너는 한국어로만 답하는 AI 어시스턴트다.")//시스템 역할 설정
                .user(message)//사용자 메시지 설정
                .call()//호출
                .content();//응답 내용만 추출
    }
    @Autowired
//    private AiService aiService;
    private AiServiceByChatClient aiService;
    
    // ##### 요청 매핑 메소드 #####
    @PostMapping(
      value = "/chat-model",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
      produces = MediaType.TEXT_PLAIN_VALUE
    )
    public String chatModel(@RequestParam("question") String question) {
      String answerText = aiService.generateText(question);
      return answerText;
    }

    @PostMapping(
      value = "/chat-model-stream",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
      produces = MediaType.APPLICATION_NDJSON_VALUE //라인으로 구분된 청크 텍스트
    )
    public Flux<String> chatModelStream(@RequestParam("question") String question) {
      Flux<String> answerStreamText = aiService.generateStreamText(question);
      return answerStreamText;
    }
    
    
    //rag관련
    @PostMapping(
    	    value = "/txt-pdf-docx-etl",
    	    consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
    	    produces = MediaType.TEXT_PLAIN_VALUE
    	  )
    	  public String txtPdfDocxEtl(
    	    @RequestParam("title") String title, 
    	    @RequestParam("author") String author, 
    	    @RequestParam("attach") MultipartFile attach) throws Exception {
    	    String result = etlService.etlFromFile(title, author, attach);
    	    return result;
    	  } 

    	  @PostMapping(
    	    value = "/html-etl",
    	    consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
    	    produces = MediaType.TEXT_PLAIN_VALUE
    	  )
    	  public String htmlEtl(
    	    @RequestParam("title") String title, 
    	    @RequestParam("author") String author, 
    	    @RequestParam("url") String url) throws Exception {
    	    String result = etlService.etlFromHtml(title, author, url);
    	    return result;
    	  } 
    	  
    	  @PostMapping(
    	    value = "/json-etl",
    	    consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
    	    produces = MediaType.TEXT_PLAIN_VALUE
    	  )
    	  public String jsonEtl(@RequestParam("url") String url) throws Exception {
    	    String result = etlService.etlFromJson(url);
    	    return result;
    	  }  

    	  @GetMapping(
    	    value = "/rag-clear",
    	    produces = MediaType.TEXT_PLAIN_VALUE
    	  )
    	  public String ragClear() {
    	    ragService1.clearVectorStore();
    	    return "벡터 저장소의 데이터를 모두 삭제했습니다.";
    	  }   

    	  @PostMapping(
    	    value = "/rag-etl",
    	    consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
    	    produces = MediaType.TEXT_PLAIN_VALUE
    	  )
    	  public String ragEtl(
    	    @RequestParam("attach") MultipartFile attach,
    	    @RequestParam("source") String source,
    	    @RequestParam(value = "chunkSize", defaultValue = "200") int chunkSize, 
    	    @RequestParam(value = "minChunkSizeChars", defaultValue = "100") int minChunkSizeChars 
    	  ) throws Exception {
    	    ragService1.ragEtl(attach, source, chunkSize, minChunkSizeChars);
    	    return "PDF ETL 과정을 성공적으로 처리했습니다.";
    	  }   

    	  @PostMapping(
    	    value = "/rag-chat",
    	    consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
    	    produces = MediaType.TEXT_PLAIN_VALUE
    	  )
    	  public String ragChat(
    	    @RequestParam("question") String question,
    	    @RequestParam(value = "score", defaultValue = "0.0") double score,
    	    @RequestParam("source") String source
    	  ) {
    	    String answer = ragService1.ragChat(question, score, source);
    	    return answer;
    	  }

    	  @PostMapping(
    	    value = "/compression-query-transformer",
    	    consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
    	    produces = MediaType.TEXT_PLAIN_VALUE
    	  )
    	  public String compressionQueryTransformer(
    	    @RequestParam("question") String question,
    	    @RequestParam(value = "score", defaultValue = "0.0") double score,
    	    @RequestParam("source") String source,
    	    HttpSession session
    	  ) {
    	    String answer = ragService2.chatWithCompression(question, score, source, session.getId());
    	    return answer;
    	  }  

    	  @PostMapping(
    	    value = "/rewrite-query-transformer",
    	    consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
    	    produces = MediaType.TEXT_PLAIN_VALUE
    	  )
    	  public String rewriteQueryTransformer(
    	    @RequestParam("question") String question,
    	    @RequestParam(value = "score", defaultValue = "0.0") double score,
    	    @RequestParam("source") String source
    	  ) {
    	    String answer = ragService2.chatWithRewriteQuery(question, score, source);
    	    return answer;
    	  }   

    	  @PostMapping(
    	    value = "/translation-query-transformer",
    	    consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
    	    produces = MediaType.TEXT_PLAIN_VALUE
    	  )
    	  public String translationQueryTransformer(
    	    @RequestParam("question") String question,
    	    @RequestParam(value = "score", defaultValue = "0.0") double score,
    	    @RequestParam("source") String source
    	  ) {
    	    String answer = ragService2.chatWithTranslation(question, score, source);
    	    return answer;
    	  } 
    	  
    	  @PostMapping(
    	    value = "/multi-query-expander",
    	    consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
    	    produces = MediaType.TEXT_PLAIN_VALUE
    	  )
    	  public String multiQueryExpander(
    	    @RequestParam("question") String question,
    	    @RequestParam(value = "score", defaultValue = "0.0") double score,
    	    @RequestParam("source") String source
    	  ) {
    	    String answer = ragService2.chatWithMultiQuery(question, score, source);
    	    return answer;
    	  }    
    	  
}