### 프로젝트 위치
- 실제 소스코드는 `demo/` 폴더에 있습니다.
- 127.0.0.1:8080 로 접속하여 테스트(api-key 필요)

### PostgreSQL (pgvector) 설정 방법
1. 도커 설치
2. C:\spring-ai-course\docker\pgvector 위치에서 터미널(powershell) 실행 후 .\pgvector.ps1 실행
-> 정책때문에 실행이 안되면 아래 명령문 입력
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
3. 2번 실행되면 도커에 생성됨. containers에서 실행하면 됨.
4. C:\spring-ai-course\tools\setup\pgadmin4-9.11-x64 설치 후 접속
5. postgre 프로그램에서 servers 오른쪽마우스> register> server
-> General탭에 Name: pgvector
-> Connection 탭은 application에 datasource 보고 설정하기



### Agentic AI 관련 소스

## 전체 구조 (AgentController + AgentService + Tool)
- controller
  - AgentController : HTTP 요청 받음, 파라미터 받음, AgentService 호출, 결과 반환

- service: 전체 흐름 관리, ai가 프롬프트에 정의된 규칙을 보고 질문 분류, 분류 결과에 따라 Tool 실행, 최종 응답 조합
  - AgentService

- 실행
  - AiServiceByChatClient
  - RagService1 : 'source' 없으면 전체 검색으로 처리
  - WeatherTool(샘플용 하드코딩)
  - RagTool
  - DbTool(샘플용 하드코딩)

- 기타 변경
  - html 파일, HomeController 등
    

## 전체 흐름
1. 사용자요청 
2. AgentController
3. AgentService
4. Tool 결정 (Rag/Db/Weather/일반 대화)
→ AgentService가 실제 실행
→ 응답 검토 → 판단후 재실행 또는 최종 응답으로 자연어 처리
→ 응답 반환
