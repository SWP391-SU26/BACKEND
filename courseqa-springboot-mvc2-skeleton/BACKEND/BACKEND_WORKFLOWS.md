# Backend Workflows - CourseQA Spring Boot MVC2

Tài liệu này tóm tắt các workflow chính của backend hiện tại để team có thể chia việc và nối frontend/AI engine dễ hơn.

## 1. Tổng quan kiến trúc

Backend dùng Spring Boot MVC2:

```text
Controller -> Service -> Repository -> SQL Server
```

Các nhóm module chính:

| Module | Controller | Service | Chức năng |
|---|---|---|---|
| Auth/User | `AuthController` | `AuthService` | Đăng ký, đăng nhập, logout, quản lý role |
| Course/Workspace | `CourseController` | `CourseService` | Lấy course và workspace |
| Document | `DocumentController` | `DocumentService` | Upload tài liệu, extract text, chia chunk, preview/download |
| RAG/Retrieval | `RagController` | `EmbeddingService`, `RetrievalService` | Quản lý embedding model, chuẩn bị embedding, retrieve chunk |
| Chat/Notes | `ChatController` | `ChatService`, `NoteService` | Tạo session, xem history, lưu note |
| Evaluation | `EvaluationController` | `EvaluationService` | Tạo dataset, câu hỏi, experiment, xem result |
| Fine-tuning | `FineTuningController` | `FineTuningService` | Export JSONL, tạo fine-tuning experiment |
| AI Bridge | Không có controller riêng | `AIClientService` | Gọi Python AI Engine tại `http://localhost:8001` |

Base URL mặc định: `http://localhost:8080`

Response JSON phổ biến:

```json
{
  "success": true,
  "message": null,
  "data": {}
}
```

## 2. Workflow khởi tạo dữ liệu

Mục tiêu: chuẩn bị database để backend chạy được.

1. Chạy SQL script: `database/VietnameseCourseQA20DB.sql`
2. Script tạo database `VietnameseCourseQA20DB`.
3. Script tạo bảng theo thứ tự phụ thuộc: users, courses, workspaces, documents, chunks, RAG, chat, evaluation, experiments.
4. Script seed dữ liệu demo:
   - Users: admin, teacher, student, researcher.
   - Roles: ADMIN, TEACHER, STUDENT, RESEARCHER.
   - Course demo: `AI101`.
   - Workspace demo: `AI101 Course Workspace`.
   - Embedding models demo: `multilingual-e5-base`, `text-embedding-3-small`, `PhoBERT-base`, `bge-m3`.

Config backend nằm ở:

```text
src/main/resources/application.properties
```

Các config quan trọng:

```properties
server.port=8080
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=VietnameseCourseQA20DB;encrypt=true;trustServerCertificate=true
spring.jpa.hibernate.ddl-auto=none
app.upload-dir=uploads
python.ai.service.url=http://localhost:8001
```

## 3. Workflow Auth/User

Mục tiêu: user đăng ký, đăng nhập và backend quản lý role.

Luồng chính:

```text
Frontend
  -> /api/auth/register hoặc /api/auth/login
  -> AuthController
  -> AuthService
  -> UserRepository/UserRoleRepository
  -> SQL Server
```

API chính:

| API | Mục đích |
|---|---|
| `POST /api/auth/register` | Tạo user mới, hash password, gán role |
| `POST /api/auth/login` | Kiểm tra email/password, cập nhật `last_login_at` |
| `POST /api/auth/logout/{userId}` | Cập nhật `last_logout_at` |
| `GET /api/auth/users` | Lấy danh sách user kèm role |
| `GET /api/auth/users/{userId}/roles` | Lấy role đang active của user |
| `PUT /api/auth/users/{userId}/role` | Deactivate role cũ và tạo role mới |

Bảng liên quan:

```text
users
user_roles
```

Ghi chú hiện trạng:

- Backend đang trả một token random UUID trong `AuthResponse`, chưa phải JWT thật.
- Role `USER` được normalize thành `STUDENT`.

## 4. Workflow Course/Workspace

Mục tiêu: frontend lấy course và workspace để user chọn phạm vi học/tài liệu/chat.

Luồng chính:

```text
Frontend
  -> /api/courses
  -> CourseController
  -> CourseService
  -> CourseRepository/CourseWorkspaceRepository
  -> SQL Server
```

API chính:

| API | Mục đích |
|---|---|
| `GET /api/courses` | Lấy course đang active |
| `GET /api/courses/workspaces` | Lấy tất cả workspace đang active |
| `GET /api/courses/{courseId}/workspaces` | Lấy workspace theo course |

Bảng liên quan:

```text
courses
chapters
course_workspaces
```

Ghi chú hiện trạng:

- Hiện mới có API đọc course/workspace.
- DTO có các request create/update nhưng controller hiện chưa expose API tạo/sửa/xóa course/workspace.

## 5. Workflow Document Processing

Mục tiêu: upload tài liệu môn học, extract text, lưu page, chia chunk để phục vụ RAG.

Luồng upload:

```text
Frontend upload file
  -> POST /api/documents/upload
  -> DocumentController
  -> DocumentService.uploadDocument()
  -> Lưu file vào uploads/
  -> Lưu course_documents với status PROCESSING
  -> Extract text theo file type
  -> Lưu document_pages
  -> Chia chunk fixed_1200_150
  -> Lưu document_chunks
  -> Update course_documents status PROCESSED/NO_TEXT/FAILED
```

File type hỗ trợ extract:

| File type | Cách xử lý |
|---|---|
| PDF | Apache PDFBox, extract từng page |
| DOCX | Apache POI, extract thành 1 page text |
| PPTX | Apache POI, extract theo slide |
| TXT/MD/CSV | Đọc text UTF-8 |

API chính:

| API | Mục đích |
|---|---|
| `POST /api/documents/upload` | Upload và xử lý tài liệu |
| `GET /api/documents/workspace/{workspaceId}` | Danh sách tài liệu trong workspace |
| `GET /api/documents/{documentId}` | Chi tiết document |
| `GET /api/documents/{documentId}/pages` | Text đã extract theo page |
| `GET /api/documents/{documentId}/chunks` | Chunk đã chia |
| `GET /api/documents/{documentId}/file` | Download file gốc |
| `GET /api/documents/{documentId}/preview` | Preview PDF |
| `DELETE /api/documents/{documentId}` | Xóa document, pages, chunks, result liên quan |

Bảng liên quan:

```text
course_documents
document_pages
document_chunks
retrieval_results
answer_citations
saved_notes
```

Ghi chú hiện trạng:

- Chunk strategy đang hard-code là `fixed_1200_150`.
- Preview PDF cho DOCX/PPTX cần LibreOffice trên máy backend.
- Khi xóa document, backend có xử lý dọn `answer_citations`, `retrieval_results`, `saved_notes`, pages, chunks và file local.

## 6. Workflow RAG/Retrieval

Mục tiêu: chuẩn bị embedding model, tạo embedding demo cho chunk, retrieve chunk phù hợp với câu hỏi.

Luồng chuẩn bị embedding:

```text
Document đã có chunks
  -> POST /api/rag/embeddings/prepare
  -> EmbeddingService.prepareEmbeddings()
  -> Chọn embedding model
  -> Lấy chunks theo documentId hoặc workspaceId
  -> Tạo vector hash local
  -> Lưu chunk_embeddings
```

Luồng retrieve:

```text
Frontend/Pipeline gửi query
  -> POST /api/rag/retrieve
  -> RetrievalService.retrieve()
  -> Lấy chunks trong workspace
  -> Tính keyword cosine score
  -> Filter theo threshold
  -> Sort score giảm dần
  -> Trả topK chunks
  -> Nếu có chatSessionId + userMessageId thì lưu retrieval_queries và retrieval_results
```

API chính:

| API | Mục đích |
|---|---|
| `GET /api/rag/embedding-models` | Lấy embedding models |
| `POST /api/rag/embedding-models` | Tạo embedding model |
| `POST /api/rag/embeddings/prepare` | Tạo embeddings cho document/workspace |
| `POST /api/rag/retrieve` | Retrieve chunk, có thể không persist |
| `POST /api/rag/retrieval-queries` | Alias tạo retrieval query bằng retrieve |
| `GET /api/rag/retrieval-queries?workspaceId=...` | Lấy lịch sử query |
| `GET /api/rag/retrieval-results?retrievalQueryId=...` | Lấy result của query |
| `GET /api/rag/citations?assistantMessageId=...` | Lấy citations |

Bảng liên quan:

```text
embedding_models
chunk_embeddings
document_chunks
retrieval_queries
retrieval_results
answer_citations
```

Ghi chú hiện trạng:

- Embedding hiện là demo local hash vector, chưa gọi model embedding thật.
- Retrieval hiện dùng keyword cosine trên token text, không đọc trực tiếp vector JSON trong `chunk_embeddings`.
- Persist retrieval chỉ xảy ra khi request có đủ `chatSessionId` và `userMessageId`.

## 7. Workflow Chat/Notes

Mục tiêu: tạo chat session theo user/workspace, lưu lịch sử chat, lưu note.

Luồng tạo session:

```text
Frontend
  -> POST /api/chat/sessions
  -> ChatService.createOrGetSession()
  -> Nếu user/workspace đã có active session thì trả session cũ
  -> Nếu chưa có thì lấy workspace để gán courseId
  -> Lưu chat_sessions
```

Luồng hỏi đáp dự kiến:

```text
Frontend gửi câu hỏi
  -> POST /api/chat/sessions/{sessionId}/ask
  -> Lưu user message
  -> Retrieve relevant chunks
  -> Gọi Python AI Engine /ai/chat
  -> Lưu assistant message
  -> Lưu citations
  -> Trả answer + citations cho frontend
```

API chính:

| API | Mục đích |
|---|---|
| `POST /api/chat/sessions` | Tạo hoặc lấy active chat session |
| `POST /api/chat/sessions/{sessionId}/ask` | Hỏi AI |
| `GET /api/chat/sessions/{sessionId}/history` | Lấy tối đa 50 messages |
| `POST /api/chat/notes` | Lưu note thủ công |
| `GET /api/chat/notes/workspace/{workspaceId}` | Lấy note theo workspace |

Bảng liên quan:

```text
chat_sessions
chat_messages
retrieval_queries
retrieval_results
answer_citations
saved_notes
```

Ghi chú hiện trạng:

- `ChatService.askQuestion()` hiện đang throw `UnsupportedOperationException`.
- Backend đã có `AIClientService.callChat()` để gọi Python `/ai/chat`, nhưng chưa nối vào `ChatService`.
- Note hiện là note thủ công với `note_type = MANUAL`.

## 8. Workflow Evaluation

Mục tiêu: tạo bộ câu hỏi benchmark, tạo experiment, chạy/chấm kết quả RAG hoặc fine-tuning.

Luồng tạo dataset:

```text
Researcher/Teacher
  -> POST /api/evaluation/datasets
  -> EvaluationService.createDataset()
  -> Lưu evaluation_datasets
```

Luồng thêm câu hỏi:

```text
Researcher/Teacher
  -> POST /api/evaluation/questions
  -> Kiểm tra dataset tồn tại
  -> question_no = số câu hiện có + 1
  -> Lưu evaluation_questions
```

Luồng tạo experiment:

```text
Researcher
  -> POST /api/evaluation/experiments
  -> Load dataset để lấy courseId/workspaceId
  -> Lưu experiments với status PENDING
```

Luồng chạy benchmark dự kiến:

```text
Researcher
  -> POST /api/evaluation/experiments/{experimentId}/run
  -> Load experiment
  -> Load questions theo dataset
  -> Gọi Python AI Engine /ai/benchmark
  -> Lưu experiment_results
  -> Update experiment status COMPLETED/FAILED
```

API chính:

| API | Mục đích |
|---|---|
| `GET /api/evaluation/datasets` | Lấy tất cả dataset |
| `POST /api/evaluation/datasets` | Tạo dataset |
| `POST /api/evaluation/questions` | Thêm câu hỏi vào dataset |
| `GET /api/evaluation/datasets/{datasetId}/questions` | Lấy câu hỏi của dataset |
| `POST /api/evaluation/experiments` | Tạo experiment |
| `GET /api/evaluation/experiments` | Lấy tất cả experiment |
| `POST /api/evaluation/experiments/{experimentId}/run` | Chạy benchmark |
| `GET /api/evaluation/experiments/{experimentId}/results` | Lấy kết quả experiment |

Bảng liên quan:

```text
evaluation_datasets
evaluation_questions
experiments
experiment_results
v_experiment_dashboard
```

Ghi chú hiện trạng:

- CRUD dataset/question/experiment cơ bản đã có.
- `runBenchmark()` hiện đang throw `UnsupportedOperationException`.
- SQL có view `v_experiment_dashboard` để tổng hợp metric.

## 9. Workflow Fine-tuning

Mục tiêu: export dữ liệu train JSONL từ evaluation dataset và quản lý record experiment fine-tuning.

Luồng export JSONL:

```text
Researcher
  -> POST /api/fine-tuning/export-jsonl/{datasetId}
  -> FineTuningService.exportJsonl()
  -> Load questions trong dataset
  -> Mỗi dòng JSONL: {"prompt": questionText, "completion": groundTruthAnswer}
  -> Trả file download dataset_{datasetId}_train.jsonl
```

Luồng tạo fine-tuning experiment:

```text
Researcher
  -> POST /api/fine-tuning/experiments
  -> Load dataset
  -> Lưu experiments với experiment_type = FINE_TUNING, status = PENDING
```

API chính:

| API | Mục đích |
|---|---|
| `POST /api/fine-tuning/export-jsonl/{datasetId}` | Download JSONL training data |
| `GET /api/fine-tuning/files` | Generate danh sách tên file theo experiments |
| `POST /api/fine-tuning/experiments` | Tạo fine-tuning experiment record |

Bảng/file liên quan:

```text
evaluation_datasets
evaluation_questions
experiments
data/fine_tuning/courseqa_train.jsonl
```

Ghi chú hiện trạng:

- Export JSONL đang tạo file download trong memory, không ghi file xuống disk.
- Chưa có workflow gọi provider fine-tuning thật.

## 10. Workflow AI Bridge

Mục tiêu: backend gọi Python AI Engine khi phần AI sẵn sàng.

Service: `AIClientService`

Endpoint Python dự kiến:

| Method | Python endpoint | Mục đích |
|---|---|---|
| POST | `/ai/chat` | RAG chat pipeline |
| POST | `/ai/chat-finetuned` | Fine-tuned chat pipeline |
| POST | `/ai/evaluate` | LLM-as-judge |
| POST | `/ai/benchmark` | Benchmark/RAGAS runner |

Config:

```properties
python.ai.service.url=http://localhost:8001
```

Timeout/retry:

| Nhóm call | Timeout | Retry |
|---|---:|---:|
| Chat/evaluate | 30s | 3 lần với lỗi retryable |
| Benchmark | 120s | 3 lần với lỗi retryable |

Ghi chú hiện trạng:

- `AIClientService` đã có sẵn, nhưng `ChatService.askQuestion()` và `EvaluationService.runBenchmark()` chưa gọi service này.
- Cần thống nhất contract request/response với Python AI Engine trước khi nối end-to-end.

## 11. Workflow end-to-end đề xuất cho demo

Luồng demo học viên hỏi đáp tài liệu:

```text
1. Login
2. Chọn course/workspace
3. Teacher upload tài liệu
4. Backend extract text và chia chunks
5. Prepare embeddings cho workspace/document
6. Student tạo chat session
7. Student hỏi câu hỏi
8. Backend retrieve chunks liên quan
9. Backend gọi Python /ai/chat
10. Backend lưu chat message + citations
11. Frontend hiển thị answer, citations, history
12. Student lưu note nếu cần
```

Luồng demo nghiên cứu RAG vs Fine-tuning:

```text
1. Researcher tạo evaluation dataset
2. Thêm question + ground truth answer
3. Export JSONL để fine-tuning
4. Tạo experiment RAG
5. Tạo experiment FINE_TUNING
6. Chạy benchmark từng experiment
7. Lưu experiment_results
8. So sánh metric trong dashboard/view
```

## 12. Phân công workflow gợi ý

Theo file `WORK_DIVISION_4_MEMBERS.md`, có thể chia như sau:

| Member | Workflow phụ trách | Việc nên ưu tiên |
|---|---|---|
| Member 1 | Auth + Course/Workspace | Hoàn thiện create/update course/workspace nếu frontend cần; kiểm tra role và response auth |
| Member 2 | Document Processing | Test upload PDF/DOCX/PPTX/TXT; kiểm tra preview; tối ưu chunking |
| Member 3 | RAG + Chat | Nối `ChatService.askQuestion()` với retrieval và `AIClientService.callChat()` |
| Member 4 | Evaluation + Fine-tuning + API docs | Nối `runBenchmark()` với `AIClientService.callBenchmark()`; chuẩn hóa JSONL/export/API docs |

## 13. Những điểm còn TODO/rủi ro

| Hạng mục | Trạng thái hiện tại | Cần làm tiếp |
|---|---|---|
| Auth token | Token random UUID | Nếu cần bảo mật thật, thêm JWT/session |
| Course CRUD | Chủ yếu mới read | Thêm create/update/delete nếu admin UI cần |
| Chat ask | Chưa implement | Lưu user message, retrieve, call AI, lưu assistant/citation |
| Benchmark run | Chưa implement | Gọi Python benchmark, lưu results, update status |
| Embedding thật | Demo hash vector | Nối model embedding thật hoặc Python service |
| Retrieval vector search | Keyword cosine | Dùng vector similarity nếu đã có embedding thật |
| Fine-tuning thật | Chỉ export JSONL và tạo record | Nối provider fine-tuning hoặc Python service |
| Encoding docs cũ | Một số README/comment bị lỗi dấu | Nên lưu lại file bằng UTF-8 nếu cần trình bày |

