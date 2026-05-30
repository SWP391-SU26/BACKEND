# PROJECT CONTEXT v4.1 — SU26SWP09
> Phiên bản: v4.1 — Cập nhật thực tế sau khi đã setup xong Python AI Engine
> Dành cho: Member 3 (bạn) — phụ trách Python AI Engine + Java Member 3
> Paste file này vào Copilot/Claude Code mỗi khi bắt đầu phiên làm việc mới

---

## 1. TỔNG QUAN DỰ ÁN

**Tên:** Chatbot hỏi đáp tài liệu môn học — So sánh RAG vs Fine-tuning (tiếng Việt)
**Trường:** FPT University HCMC
**Thời hạn:** 1 tháng
**FE:** React (đang chờ API từ Java)
**BE:** Java Spring Boot (API Gateway) + Python FastAPI (AI Engine)

---

## 2. KIẾN TRÚC HỆ THỐNG

```
React FE
    │ HTTP/REST
    ▼
Java Spring Boot  ←──── Azure SQL Server (business data)
(API Gateway)           package: com.courseqa
    │ HTTP nội bộ       port: 8080
    ▼
Python FastAPI    ←──── Qdrant local (dev) / GCP VM (prod)
(AI Engine)             port: 8001
    │
    ├── RAG Pipeline
    ├── Fine-tuned Pipeline (HuggingFace Inference API)
    ├── Evaluator (LLM-as-judge)
    └── RAGAS Benchmark
```

**Nguyên tắc bất biến:**
- FE chỉ giao tiếp với Java, không bao giờ gọi thẳng Python
- Java xử lý auth, CRUD, business logic, file upload
- Python chỉ nhận text input, trả AI output — không biết gì về user/session/auth
- Azure SQL = source of truth cho toàn bộ business data
- Qdrant = chỉ phục vụ vector similarity search

---

## 3. PHÂN CÔNG THỰC TẾ (3 MEMBER JAVA + 1 MEMBER PYTHON)

### Repo GitHub: SWP391-SU26/BACKEND
```
BACKEND/
├── courseqa-springboot-mvc2-skeleton/   ← Java (cả team dùng chung)
│   └── src/main/java/com/courseqa/
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── model/entity/
│       ├── model/dto/
│       ├── config/
│       └── exception/
└── backend-python/                      ← Python (bạn phụ trách)
    ├── app/ src/ api/ models/
    └── ...
```

### Member 1 (Java) — Auth + Course
```
controller/AuthController.java
controller/CourseController.java
service/AuthService.java
service/CourseService.java
entity: User, UserRole, Course, Chapter, CourseWorkspace
APIs: /api/auth/*, /api/courses/*
```

### Member 2 (Java) — Document + RAG Prep
```
controller/DocumentController.java
controller/RagController.java
service/DocumentService.java
service/EmbeddingService.java    ← gọi Python /ai/index-document
service/RetrievalService.java    ← gọi Python /ai/chat
entity: CourseDocument, DocumentPage, DocumentChunk, ChunkEmbedding...
APIs: /api/documents/*, /api/rag/*
```

### Member 3 — BẠN (Java + Python)
```
JAVA:
controller/ChatController.java
controller/EvaluationController.java
controller/FineTuningController.java
service/ChatService.java         ← gọi Python /ai/chat + /ai/chat-finetuned + /ai/evaluate
service/EvaluationService.java   ← gọi Python /ai/benchmark
service/FineTuningService.java   ← quản lý experiment records
entity: ChatSession, ChatMessage, AnswerCitation, SavedNote,
        EvaluationDataset, EvaluationQuestion, Experiment, ExperimentResult
APIs: /api/chat/*, /api/evaluation/*, /api/fine-tuning/*

PYTHON (đã xong):
backend-python/ — FastAPI AI Engine chạy port 8001
5 endpoints: /ai/index-document, /ai/chat, /ai/chat-finetuned, /ai/evaluate, /ai/benchmark
```

---

## 4. TRẠNG THÁI HIỆN TẠI (cập nhật 30/05/2026)

### ✅ Đã hoàn thành — Python AI Engine
```
backend-python/
├── app/main.py          ✅ FastAPI chạy port 8001
├── app/config.py        ✅ Settings từ .env
├── models/requests.py   ✅ Pydantic request schemas
├── models/responses.py  ✅ Pydantic response schemas
├── src/embeddings.py    ✅ 4 models: bge-m3, e5-base, openai, phobert
├── src/vector_store.py  ✅ Qdrant upsert/search/delete
├── src/rag_pipeline.py  ✅ RAG: embed → retrieve → LLM → cite
├── src/finetuned_pipeline.py ✅ HuggingFace Inference API
├── src/evaluator.py     ✅ LLM-as-judge, winner selection
├── api/index_document.py ✅ POST /ai/index-document
├── api/chat.py          ✅ POST /ai/chat
├── api/chat_finetuned.py ✅ POST /ai/chat-finetuned
├── api/evaluate.py      ✅ POST /ai/evaluate
└── api/benchmark.py     ✅ POST /ai/benchmark
```

### 🔄 Cần làm — Java Member 3
```
service/ChatService.java         ← CHƯA implement
service/EvaluationService.java   ← CHƯA implement
service/FineTuningService.java   ← CHƯA implement
controller/ChatController.java   ← CHƯA implement
controller/EvaluationController.java ← CHƯA implement
controller/FineTuningController.java ← CHƯA implement
```

---

## 5. API CONTRACT Java ↔ Python (đã chốt)

### POST /ai/index-document
```json
Request từ Java:
{
  "document_id": "uuid",
  "chunks": [{"chunk_index": 0, "content": "...", "source_page": 1, "token_count": 120}],
  "embedding_model": "bge-m3",
  "collection_name": "e4_bgem3_500_k3"
}
Response về Java:
{
  "collection_name": "e4_bgem3_500_k3",
  "point_ids": ["uuid1", "uuid2"],
  "total_chunks": 42,
  "status": "success"
}
```

### POST /ai/chat
```json
Request từ Java:
{
  "question": "Machine learning là gì?",
  "collection_name": "e4_bgem3_500_k3",
  "embedding_model": "bge-m3",
  "top_k": 5,
  "similarity_threshold": 0.72,
  "conversation_history": [{"role": "user", "content": "..."}]
}
Response về Java:
{
  "rag_answer": "...",
  "citations": [{"chunk_id": "uuid", "document_id": "uuid", "source_page": 3, "excerpt": "...", "similarity_score": 0.89}],
  "rag_score": 0.85,
  "is_out_of_scope": false,
  "tokens_used": 850,
  "latency_ms": 1200
}
```

### POST /ai/chat-finetuned
```json
Request: {"question": "...", "conversation_history": []}
Response: {"finetuned_answer": "...", "finetuned_score": 0.78, "latency_ms": 2100}
```

### POST /ai/evaluate
```json
Request: {"question": "...", "rag_answer": "...", "finetuned_answer": "...", "rag_score": 0.85, "finetuned_score": 0.78}
Response: {"winner": "rag", "scores": {"rag": 0.85, "finetuned": 0.78}, "reason": "..."}
```

### POST /ai/benchmark
```json
Request: {
  "experiment_id": "uuid",
  "config": {"collection_name": "...", "embedding_model": "bge-m3", "top_k": 5, "chunk_size": 500, "similarity_threshold": 0.72},
  "questions": [{"question_id": "uuid", "question": "...", "ground_truth": "..."}]
}
Response: {
  "experiment_id": "uuid",
  "results": [{"question_id": "uuid", "generated_answer": "...", "faithfulness": 0.92, ...}],
  "summary": {"avg_faithfulness": 0.91, "avg_answer_relevancy": 0.87, ...}
}
```

---

## 6. JAVA MEMBER 3 — CHI TIẾT CẦN IMPLEMENT

### Package: com.courseqa
### Entities đã có sẵn (không cần tạo):
```
ChatSession.java, ChatMessage.java, AnswerCitation.java, SavedNote.java
EvaluationDataset.java, EvaluationQuestion.java
Experiment.java, ExperimentResult.java
```

### Repositories đã có sẵn (không cần tạo):
```
ChatSessionRepository.java, ChatMessageRepository.java
AnswerCitationRepository.java, SavedNoteRepository.java
EvaluationDatasetRepository.java, EvaluationQuestionRepository.java
ExperimentRepository.java, ExperimentResultRepository.java
```

### AIClientService — Bridge gọi Python (cần tạo mới)
```java
// Dùng WebClient (reactive) để gọi Python AI Engine
// Python URL: http://localhost:8001
// Timeout: 30s cho chat, 120s cho benchmark
// Retry: 3 lần nếu lỗi kết nối
```

### ChatService — implement các method:
```java
createOrGetSession(userId, workspaceId) → ChatSession
askQuestion(sessionId, question) → ChatMessageResponse
  1. Lấy session + conversation history
  2. Gọi song song:
     - Python /ai/chat (RAG)
     - Python /ai/chat-finetuned
  3. Gọi Python /ai/evaluate → lấy winner
  4. Lưu ChatMessage + AnswerCitation vào SQL
  5. Trả về FE
getHistory(sessionId) → List<ChatMessage>
saveNote(userId, workspaceId, content) → SavedNote
```

### EvaluationService — implement:
```java
createDataset(name, subjectId) → EvaluationDataset
addQuestion(datasetId, question, groundTruth) → EvaluationQuestion
createExperiment(config) → Experiment
runBenchmark(experimentId):
  1. Load dataset questions từ SQL
  2. Gọi Python /ai/benchmark
  3. Lưu ExperimentResult vào SQL
  4. Update Experiment status = COMPLETED
getResults(experimentId) → List<ExperimentResult>
```

### FineTuningService — implement:
```java
createExperimentRecord(config) → Experiment
exportJsonl(datasetId) → File (train.jsonl)
listFiles() → List<String>
```

### APIs cần implement:

**Chat APIs:**
```
POST /api/chat/sessions                          → createOrGetSession
POST /api/chat/sessions/{sessionId}/ask          → askQuestion
GET  /api/chat/sessions/{sessionId}/history      → getHistory
POST /api/chat/notes                             → saveNote
GET  /api/chat/notes/workspace/{workspaceId}     → getNotes
```

**Evaluation APIs:**
```
GET  /api/evaluation/datasets                    → listDatasets
POST /api/evaluation/datasets                    → createDataset
POST /api/evaluation/questions                   → addQuestion
GET  /api/evaluation/datasets/{id}/questions     → getQuestions
POST /api/evaluation/experiments                 → createExperiment
GET  /api/evaluation/experiments                 → listExperiments
POST /api/evaluation/experiments/{id}/run        → runBenchmark
GET  /api/evaluation/experiments/{id}/results    → getResults
```

**Fine-tuning APIs:**
```
POST /api/fine-tuning/export-jsonl/{datasetId}   → exportJsonl
GET  /api/fine-tuning/files                      → listFiles
POST /api/fine-tuning/experiments                → createExperimentRecord
```

---

## 7. DATABASE

**Azure SQL Server — package com.courseqa**
**File SQL:** `courseqa-springboot-mvc2-skeleton/database/VietnameseCourseQA20DB.sql`

Bảng Member 3 dùng:
```sql
chat_sessions       -- id, user_id, workspace_id, created_at, is_active
chat_messages       -- id, session_id, role, content, rag_answer, finetuned_answer, winner, tokens_used, latency_ms
answer_citations    -- id, message_id, chunk_id, document_id, source_page, excerpt, similarity_score
saved_notes         -- id, user_id, workspace_id, content, created_at
evaluation_datasets -- id, name, subject_id, question_count, created_by
evaluation_questions-- id, dataset_id, question_text, ground_truth_answer
experiments         -- id, name, researcher_id, config_json, status, created_at
experiment_results  -- id, experiment_id, question_id, generated_answer,
                    --   faithfulness, answer_relevancy, context_precision, context_recall,
                    --   latency_ms, cost_usd
```

**Qdrant collections (8 experiments):**
```
e1_e5base_300_k3, e2_e5base_500_k3, e3_e5base_800_k3
e4_bgem3_500_k3 (default), e5_openai_500_k3
e6_bgem3_500_k5, e7_bgem3_800_k5, e8_phobert_500_k3
```

---

## 8. JAVA CODING CONVENTIONS

```java
// Package
package com.courseqa.service;  // hoặc controller, repository...

// Lombok — dùng đầy đủ
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@RequiredArgsConstructor @Slf4j

// Response wrapper — dùng ApiResponse<T> có sẵn trong project
return ResponseEntity.ok(ApiResponse.success(data));
return ResponseEntity.badRequest().body(ApiResponse.error("message"));

// Gọi Python — dùng WebClient (KHÔNG dùng RestTemplate)
webClient.post()
    .uri("/ai/chat")
    .bodyValue(request)
    .retrieve()
    .bodyToMono(ChatResponse.class)
    .timeout(Duration.ofSeconds(30))
    .block();

// Exception — dùng GlobalExceptionHandler có sẵn
throw new ResourceNotFoundException("ChatSession", "id", sessionId);

// Naming
camelCase: method/variable
PascalCase: class
UPPER_SNAKE: constants
```

---

## 9. PYTHON AI ENGINE — CONVENTIONS (đã implement)

```python
# Tất cả endpoints async
async def endpoint(request: RequestModel) -> ResponseModel:

# Logging — loguru
from loguru import logger
logger.info("message")

# Error codes
RAG-001: Out of scope
RAG-002: Qdrant connection failed
RAG-003: LLM API timeout
IDX-002: Chunk embedding failed
FT-001:  HuggingFace API error
BM-002:  RAGAS evaluation failed

# Python AI URL
http://localhost:8001  (dev)
```

---

## 10. THỨ TỰ BUILD JAVA MEMBER 3

### Ngày 1 — AIClientService (quan trọng nhất)
```java
// Tạo AIClientService.java trong service/
// WebClient config, timeout, retry
// Methods: callChat(), callChatFinetuned(), callEvaluate(), callBenchmark()
// Test bằng cách gọi Python đang chạy ở port 8001
```

### Ngày 2 — ChatService + ChatController
```java
// Implement createOrGetSession, askQuestion, getHistory
// askQuestion phải gọi song song RAG + finetuned → evaluate
// Lưu ChatMessage + AnswerCitation
```

### Ngày 3 — EvaluationService + EvaluationController
```java
// Dataset CRUD, Question CRUD
// runBenchmark: gọi Python /ai/benchmark → lưu ExperimentResult
```

### Ngày 4 — FineTuningService + FineTuningController
```java
// Experiment record management
// Export JSONL từ EvaluationQuestion table
```

### Ngày 5 — Integration test
```java
// Test end-to-end: Postman → Java → Python → Qdrant → LLM
// Kiểm tra data lưu đúng vào SQL
```

---

## 11. ENVIRONMENT

### application.properties (Java)
```properties
# Azure SQL
spring.datasource.url=jdbc:sqlserver://your-server.database.windows.net:1433;database=VietnameseCourseQA20DB
spring.datasource.username=your_user
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
spring.jpa.hibernate.ddl-auto=none

# Python AI Engine URL
python.ai.service.url=http://localhost:8001

# Server
server.port=8080
```

### .env (Python) — đã có tại backend-python/.env
```env
QDRANT_HOST=localhost
QDRANT_PORT=6333
OPENAI_API_KEY=sk-...
LLM_MODEL=gpt-4o-mini
EMBEDDING_MODEL_DEFAULT=bge-m3
PYTHON_AI_PORT=8001
```

---

## 12. CHECKLIST DELIVERABLES MEMBER 3

### Python ✅ Xong
- [x] FastAPI chạy port 8001
- [x] 5 endpoints implement đầy đủ
- [x] RAG pipeline hoạt động
- [x] Fine-tuned pipeline (chờ model HuggingFace tuần 3)
- [x] Evaluator LLM-as-judge
- [x] RAGAS benchmark runner

### Java 🔄 Đang làm
- [ ] AIClientService — WebClient gọi Python
- [ ] ChatService — createSession, askQuestion, getHistory
- [ ] ChatController — 5 endpoints chat
- [ ] EvaluationService — dataset, experiment, benchmark
- [ ] EvaluationController — 8 endpoints evaluation
- [ ] FineTuningService — export JSONL, experiment records
- [ ] FineTuningController — 3 endpoints fine-tuning
- [ ] Integration test Java ↔ Python

---

## 13. NGUỒN THAM KHẢO

- Spring WebClient: https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html
- RAGAS: https://docs.ragas.io/en/v0.1.21/concepts/metrics/
- Qdrant Python client: https://python-client.qdrant.tech/
- BAAI/bge-m3: https://huggingface.co/BAAI/bge-m3
- Unsloth fine-tuning: https://github.com/unslothai/unsloth
