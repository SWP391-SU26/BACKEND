# PROJECT CONTEXT v4.2 — SU26SWP09
> Phiên bản: v4.2 — Java Member 3 only
> Dành cho: Member 3 — chỉ phụ trách Java Spring Boot
> Python AI Engine do TV6 phụ trách riêng
> Paste file này vào Copilot/Claude Code mỗi khi bắt đầu phiên làm việc mới

---

## 1. TỔNG QUAN DỰ ÁN

**Tên:** Chatbot hỏi đáp tài liệu môn học — So sánh RAG vs Fine-tuning (tiếng Việt)
**Trường:** FPT University HCMC
**FE:** React (đang chờ API từ Java)
**BE:** Java Spring Boot (API Gateway) + Python FastAPI (TV6 phụ trách)

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
Python FastAPI (TV6)    port: chờ xác nhận từ TV6
    ├── RAG Pipeline
    ├── Fine-tuned Pipeline
    ├── Evaluator
    └── RAGAS Benchmark
```

**Nguyên tắc:**
- FE chỉ giao tiếp với Java
- Java xử lý auth, CRUD, business logic
- Python do TV6 làm — Java chỉ gọi HTTP
- Azure SQL = toàn bộ business data

---

## 3. PHÂN CÔNG

### Repo: SWP391-SU26/BACKEND
```
BACKEND/
├── courseqa-springboot-mvc2-skeleton/
│   └── courseqa-springboot-mvc2-skeleton/
│       └── src/main/java/com/courseqa/
│           ├── config/
│           ├── controller/
│           ├── exception/
│           ├── model/entity/
│           ├── model/dto/
│           ├── repository/
│           └── service/
└── backend-python/   ← TV6 phụ trách, bạn không đụng vào
```

### Member 3 — BẠN (Java only)
```
Files cần implement:
service/ChatService.java
service/EvaluationService.java
service/FineTuningService.java
service/AIClientService.java    ← đã tạo, chờ API contract từ TV6
controller/ChatController.java
controller/EvaluationController.java
controller/FineTuningController.java
```

---

## 4. TRẠNG THÁI HIỆN TẠI (31/05/2026)

```
✅ AIClientService.java  — đã tạo, chờ confirm URL/format từ TV6
⏳ ChatService.java      — làm phần SQL trước, TODO phần gọi Python
⏳ EvaluationService.java — làm phần SQL trước, TODO phần gọi Python
⏳ FineTuningService.java — làm được 100% không cần Python
⏳ ChatController.java
⏳ EvaluationController.java
⏳ FineTuningController.java
```

---

## 5. API CONTRACT Java ↔ Python

> ⚠️ Chờ xác nhận từ TV6 — phần dưới là dự kiến, có thể thay đổi
> Khi TV6 confirm thì update AIClientService.java cho khớp

### POST /ai/chat (dự kiến)
```json
Request:
{
  "question": "...",
  "collection_name": "e4_bgem3_500_k3",
  "embedding_model": "bge-m3",
  "top_k": 5,
  "similarity_threshold": 0.72,
  "conversation_history": [{"role": "user", "content": "..."}]
}
Response:
{
  "rag_answer": "...",
  "citations": [{"chunk_id": "uuid", "document_id": "uuid", "source_page": 3, "excerpt": "...", "similarity_score": 0.89}],
  "rag_score": 0.85,
  "is_out_of_scope": false,
  "tokens_used": 850,
  "latency_ms": 1200
}
```

### POST /ai/chat-finetuned (dự kiến)
```json
Request: {"question": "...", "conversation_history": []}
Response: {"finetuned_answer": "...", "finetuned_score": 0.78, "latency_ms": 2100}
```

### POST /ai/evaluate (dự kiến)
```json
Request: {"question": "...", "rag_answer": "...", "finetuned_answer": "...", "rag_score": 0.85, "finetuned_score": 0.78}
Response: {"winner": "rag", "scores": {"rag": 0.85, "finetuned": 0.78}, "reason": "..."}
```

### POST /ai/benchmark (dự kiến)
```json
Request: {
  "experiment_id": "uuid",
  "config": {"collection_name": "...", "embedding_model": "bge-m3", "top_k": 5, "chunk_size": 500, "similarity_threshold": 0.72},
  "questions": [{"question_id": "uuid", "question": "...", "ground_truth": "..."}]
}
Response: {
  "experiment_id": "uuid",
  "results": [{"question_id": "uuid", "generated_answer": "...", "faithfulness": 0.92, "answer_relevancy": 0.88, "context_precision": 0.85, "context_recall": 0.79, "latency_ms": 1200}],
  "summary": {"avg_faithfulness": 0.91, "avg_answer_relevancy": 0.87, "avg_context_precision": 0.84, "avg_context_recall": 0.78}
}
```

---

## 6. ENTITIES ĐÃ CÓ SẴN (không tạo lại)

```java
// Chat
ChatSession.java
ChatMessage.java
AnswerCitation.java
SavedNote.java

// Evaluation & Fine-tuning
EvaluationDataset.java
EvaluationQuestion.java
Experiment.java
ExperimentResult.java
```

---

## 7. REPOSITORIES ĐÃ CÓ SẴN (không tạo lại)

```java
ChatSessionRepository.java
ChatMessageRepository.java
AnswerCitationRepository.java
SavedNoteRepository.java
EvaluationDatasetRepository.java
EvaluationQuestionRepository.java
ExperimentRepository.java
ExperimentResultRepository.java
```

---

## 8. CHI TIẾT CẦN IMPLEMENT

### ChatService.java
```java
// ✅ Làm được ngay — không cần Python
createOrGetSession(Long userId, Long workspaceId) → ChatSession
getHistory(Long sessionId) → List<ChatMessage>
saveNote(Long userId, Long workspaceId, String content) → SavedNote
getNotes(Long workspaceId) → List<SavedNote>

// ⏳ TODO — chờ API contract TV6
askQuestion(Long sessionId, String question) → ChatMessageResponse
  // 1. Lấy session + 10 turns lịch sử gần nhất
  // 2. Gọi song song AIClientService.callChat() + callChatFinetuned()
  // 3. Gọi AIClientService.callEvaluate() → winner
  // 4. Lưu ChatMessage vào SQL
  // 5. Lưu AnswerCitation vào SQL (nếu có citations)
  // 6. Trả về FE
```

### EvaluationService.java
```java
// ✅ Làm được ngay — không cần Python
createDataset(String name, Long subjectId, Long createdBy) → EvaluationDataset
addQuestion(Long datasetId, String question, String groundTruth) → EvaluationQuestion
getQuestions(Long datasetId) → List<EvaluationQuestion>
createExperiment(String name, Long researcherId, String configJson) → Experiment
listExperiments() → List<Experiment>
getResults(Long experimentId) → List<ExperimentResult>

// ⏳ TODO — chờ API contract TV6
runBenchmark(Long experimentId):
  // 1. Load Experiment + questions từ SQL
  // 2. Gọi AIClientService.callBenchmark()
  // 3. Lưu từng ExperimentResult vào SQL
  // 4. Update Experiment.status = "COMPLETED"
```

### FineTuningService.java
```java
// ✅ Làm được ngay — 100% không cần Python
createExperimentRecord(String name, Long researcherId, String configJson) → Experiment
exportJsonl(Long datasetId) → ResponseEntity<Resource>
  // Load EvaluationQuestion từ SQL
  // Format thành JSONL: {"prompt": "...", "completion": "..."}
  // Return file download
listExperimentFiles() → List<String>
```

### ChatController.java
```java
POST   /api/chat/sessions                       → createOrGetSession
POST   /api/chat/sessions/{sessionId}/ask       → askQuestion
GET    /api/chat/sessions/{sessionId}/history   → getHistory
POST   /api/chat/notes                          → saveNote
GET    /api/chat/notes/workspace/{workspaceId}  → getNotes
```

### EvaluationController.java
```java
GET    /api/evaluation/datasets                         → listDatasets
POST   /api/evaluation/datasets                         → createDataset
POST   /api/evaluation/questions                        → addQuestion
GET    /api/evaluation/datasets/{datasetId}/questions   → getQuestions
POST   /api/evaluation/experiments                      → createExperiment
GET    /api/evaluation/experiments                      → listExperiments
POST   /api/evaluation/experiments/{experimentId}/run   → runBenchmark
GET    /api/evaluation/experiments/{experimentId}/results → getResults
```

### FineTuningController.java
```java
POST   /api/fine-tuning/export-jsonl/{datasetId}  → exportJsonl
GET    /api/fine-tuning/files                     → listFiles
POST   /api/fine-tuning/experiments               → createExperimentRecord
```

---

## 9. DATABASE

**Azure SQL Server**
**File:** `courseqa-springboot-mvc2-skeleton/database/VietnameseCourseQA20DB.sql`

```sql
chat_sessions        -- id, user_id, workspace_id, created_at, is_active
chat_messages        -- id, session_id, role, content, rag_answer, finetuned_answer,
                     --   winner, tokens_used, latency_ms, created_at
answer_citations     -- id, message_id, chunk_id, document_id, source_page,
                     --   excerpt, similarity_score
saved_notes          -- id, user_id, workspace_id, content, created_at
evaluation_datasets  -- id, name, subject_id, question_count, created_by, created_at
evaluation_questions -- id, dataset_id, question_text, ground_truth_answer
experiments          -- id, name, researcher_id, config_json, status, created_at
experiment_results   -- id, experiment_id, question_id, generated_answer,
                     --   faithfulness, answer_relevancy, context_precision,
                     --   context_recall, latency_ms, cost_usd
```

---

## 10. JAVA CODING CONVENTIONS

```java
// Package gốc
package com.courseqa.service;
package com.courseqa.controller;

// Lombok — dùng đầy đủ
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
@Slf4j

// Response wrapper — ApiResponse<T> có sẵn trong project
return ResponseEntity.ok(ApiResponse.success(data));
return ResponseEntity.badRequest().body(ApiResponse.error("message"));

// Exception — GlobalExceptionHandler có sẵn
throw new ResourceNotFoundException("ChatSession", "id", sessionId);

// Gọi Python — WebClient (KHÔNG dùng RestTemplate)
webClient.post()
    .uri("/ai/chat")
    .bodyValue(request)
    .retrieve()
    .bodyToMono(PythonChatResponse.class)
    .timeout(Duration.ofSeconds(30))
    .block();

// Naming conventions
camelCase   → method, variable
PascalCase  → class
UPPER_SNAKE → constant
```

---

## 11. ENVIRONMENT

### application.properties
```properties
# Azure SQL Server
spring.datasource.url=jdbc:sqlserver://your-server.database.windows.net:1433;database=VietnameseCourseQA20DB
spring.datasource.username=your_user
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
spring.jpa.hibernate.ddl-auto=none

# Python AI Engine — cập nhật sau khi có thông tin từ TV6
python.ai.service.url=http://localhost:8001

# Server
server.port=8080
```

---

## 12. THỨ TỰ BUILD

```
Hôm nay:
1. ChatService.java     — phần SQL (createSession, getHistory, saveNote)
2. ChatController.java  — 5 endpoints
3. FineTuningService.java — exportJsonl, listFiles (100% SQL)
4. FineTuningController.java

Sau khi có TV6 confirm API contract:
5. Điền TODO trong ChatService.askQuestion()
6. Điền TODO trong EvaluationService.runBenchmark()
7. Cập nhật AIClientService.java cho khớp URL/format TV6
8. Integration test
```

---

## 13. CHECKLIST

- [x] AIClientService.java — tạo xong, chờ TV6 confirm
- [ ] ChatService.java — phần SQL
- [ ] ChatController.java
- [ ] EvaluationService.java — phần SQL
- [ ] EvaluationController.java
- [ ] FineTuningService.java
- [ ] FineTuningController.java
- [ ] Điền TODO gọi Python (sau khi có TV6)
- [ ] Integration test
