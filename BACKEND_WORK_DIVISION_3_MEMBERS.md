# Backend Work Division - 3 Members

Project: **Vietnamese CourseQA: RAG vs Fine-tuning**  
Framework: **Spring Boot**  
Architecture: **MVC2**

---

## 1. Project Structure

The backend follows MVC2 structure:

```text
src/main/java/com/courseqa
├── controller
├── service
├── repository
├── model
│   ├── entity
│   └── dto
├── config
└── exception
```

Meaning:

| Layer | Purpose |
|---|---|
| `controller` | Receives API requests from frontend |
| `service` | Handles business logic |
| `repository` | Works with database |
| `model/entity` | Maps Java classes to database tables |
| `model/dto` | Defines request/response data for APIs |
| `config` | Stores project configuration |
| `exception` | Handles errors |

---

## 2. Overview of 3-Member Division

| Member | Main Responsibility | Main API Groups |
|---|---|---|
| Member 1 | Authentication + Course Management | `/api/auth`, `/api/courses` |
| Member 2 | Document Processing + RAG Preparation | `/api/documents`, `/api/rag` |
| Member 3 | Chatbot + Evaluation + Fine-tuning | `/api/chat`, `/api/evaluation`, `/api/fine-tuning` |

---

# Member 1: Authentication + Course Management

## Main Responsibility

Member 1 is responsible for the basic system foundation. This member handles users, roles, courses, chapters, and course workspaces.

## Functions

```text
1. Register
2. Login
3. Logout
4. Manage users
5. Manage user roles
6. Manage courses
7. Manage chapters
8. Manage course workspaces
```

## Database Tables

```text
users
user_roles
courses
chapters
course_workspaces
```

## Files to Handle

### Controller

```text
controller/AuthController.java
controller/CourseController.java
```

### Service

```text
service/AuthService.java
service/CourseService.java
```

### Repository

```text
repository/UserRepository.java
repository/UserRoleRepository.java
repository/CourseRepository.java
repository/ChapterRepository.java
repository/CourseWorkspaceRepository.java
```

### Model / Entity

```text
model/entity/User.java
model/entity/UserRole.java
model/entity/Course.java
model/entity/Chapter.java
model/entity/CourseWorkspace.java
```

### DTO

```text
model/dto/AuthDto.java
model/dto/CourseDto.java
```

## APIs to Complete

```http
POST /api/auth/register
POST /api/auth/login
POST /api/auth/logout/{userId}
GET  /api/auth/users
GET  /api/auth/users/{userId}/roles

GET  /api/courses
POST /api/courses
GET  /api/courses/{courseId}/chapters
POST /api/courses/{courseId}/chapters
GET  /api/courses/{courseId}/workspaces
POST /api/courses/{courseId}/workspaces
```

## Expected Output

```text
- User can register.
- User can login.
- User can logout.
- User has a role: ADMIN, TEACHER, STUDENT, or RESEARCHER.
- Teacher/Admin can create courses.
- Teacher/Admin can create chapters.
- Teacher/Admin can create course workspaces.
- Frontend can receive userId, courseId, chapterId, and workspaceId.
```

---

# Member 2: Document Processing + RAG Preparation

## Main Responsibility

Member 2 is responsible for processing course documents and preparing data for RAG. This includes uploading files, extracting text, chunking documents, creating embeddings, and preparing retrieval data.

## Functions

```text
1. Upload PDF, DOCX, PPTX, TXT files
2. Save document information
3. Extract text from uploaded documents
4. Save extracted text by page
5. Chunk document content
6. Save chunks into database
7. Manage embedding models
8. Create demo embeddings for chunks
9. Prepare retrieval queries
10. Store retrieval results
```

## Database Tables

```text
course_documents
document_pages
document_chunks
embedding_models
chunk_embeddings
retrieval_queries
retrieval_results
```

## Files to Handle

### Controller

```text
controller/DocumentController.java
controller/RagController.java
```

### Service

```text
service/DocumentService.java
service/EmbeddingService.java
service/RetrievalService.java
```

### Repository

```text
repository/CourseDocumentRepository.java
repository/DocumentPageRepository.java
repository/DocumentChunkRepository.java
repository/EmbeddingModelRepository.java
repository/ChunkEmbeddingRepository.java
repository/RetrievalQueryRepository.java
repository/RetrievalResultRepository.java
```

### Model / Entity

```text
model/entity/CourseDocument.java
model/entity/DocumentPage.java
model/entity/DocumentChunk.java
model/entity/EmbeddingModel.java
model/entity/ChunkEmbedding.java
model/entity/RetrievalQuery.java
model/entity/RetrievalResult.java
```

### DTO

```text
model/dto/DocumentDto.java
model/dto/RagDto.java
```

## APIs to Complete

```http
POST /api/documents/upload
GET  /api/documents/workspace/{workspaceId}
GET  /api/documents/{documentId}/pages
GET  /api/documents/{documentId}/chunks

GET  /api/rag/embedding-models
POST /api/rag/embedding-models
GET  /api/rag/retrieval-queries
GET  /api/rag/retrieval-results
GET  /api/rag/citations
```

## Expected Output

```text
- User can upload PDF/DOCX/PPTX/TXT files.
- Uploaded files are saved in the uploads folder.
- Text is extracted from uploaded documents.
- Extracted text is saved into document_pages.
- Text is split into chunks.
- Chunks are saved into document_chunks.
- Demo embeddings are created and saved into chunk_embeddings.
- Retrieval queries and retrieval results can be stored for chatbot use.
- Document status becomes INDEXED after successful processing.
```

---

# Member 3: Chatbot + Evaluation + Fine-tuning

## Main Responsibility

Member 3 is responsible for the user-facing chatbot and the research/evaluation module. This includes chat sessions, chat messages, source citations, notes, test sets, benchmark experiments, and fine-tuning experiment tracking.

## Functions

```text
1. Create chat sessions
2. Save chat messages
3. Generate chatbot answers from uploaded documents
4. Create source citations
5. Save learning notes
6. Create evaluation datasets
7. Add 50 questions and ground truth answers
8. Create RAG experiments
9. Create fine-tuning experiment records
10. Run benchmark demo
11. Save experiment results
12. Export fine-tuning JSONL file
13. Prepare API documentation for frontend
```

## Database Tables

```text
chat_sessions
chat_messages
answer_citations
saved_notes
evaluation_datasets
evaluation_questions
experiments
experiment_results
```

## Files to Handle

### Controller

```text
controller/ChatController.java
controller/EvaluationController.java
controller/FineTuningController.java
```

### Service

```text
service/ChatService.java
service/NoteService.java
service/EvaluationService.java
service/FineTuningService.java
```

### Repository

```text
repository/ChatSessionRepository.java
repository/ChatMessageRepository.java
repository/AnswerCitationRepository.java
repository/SavedNoteRepository.java
repository/EvaluationDatasetRepository.java
repository/EvaluationQuestionRepository.java
repository/ExperimentRepository.java
repository/ExperimentResultRepository.java
```

### Model / Entity

```text
model/entity/ChatSession.java
model/entity/ChatMessage.java
model/entity/AnswerCitation.java
model/entity/SavedNote.java
model/entity/EvaluationDataset.java
model/entity/EvaluationQuestion.java
model/entity/Experiment.java
model/entity/ExperimentResult.java
```

### DTO

```text
model/dto/ChatDto.java
model/dto/EvaluationDto.java
model/dto/FineTuningDto.java
```

### Other Files

```text
data/fine_tuning/courseqa_train.jsonl
API_SKELETON_FOR_FE.md
```

## APIs to Complete

### Chat APIs

```http
POST /api/chat/sessions
GET  /api/chat/sessions/workspace/{workspaceId}
POST /api/chat/sessions/{sessionId}/ask
GET  /api/chat/sessions/{sessionId}/history

POST /api/chat/notes
GET  /api/chat/notes/workspace/{workspaceId}
```

### Evaluation APIs

```http
GET  /api/evaluation/datasets
POST /api/evaluation/datasets
POST /api/evaluation/questions
GET  /api/evaluation/datasets/{datasetId}/questions
POST /api/evaluation/experiments
GET  /api/evaluation/experiments
POST /api/evaluation/experiments/{experimentId}/run
GET  /api/evaluation/experiments/{experimentId}/results
```

### Fine-tuning APIs

```http
POST /api/fine-tuning/export-jsonl/{datasetId}
GET  /api/fine-tuning/files
POST /api/fine-tuning/experiments
```

## Expected Output

```text
- User can create a chat session.
- User can ask questions.
- Chatbot answers based on uploaded course documents.
- If no relevant document is found, chatbot replies that the answer is not in the provided documents.
- Chat history is saved.
- Citations are created for chatbot answers.
- User can save learning notes.
- Researcher can create evaluation datasets.
- Researcher can add 50 questions and ground truth answers.
- Researcher can create RAG experiments.
- Researcher can create fine-tuning experiment records.
- Benchmark results are saved.
- Fine-tuning JSONL file can be exported.
- Frontend team has API documentation to integrate.
```

---

# 3. Integration Flow Between Members

## Step 1: Member 1 finishes base data

Member 1 must provide:

```text
userId
courseId
chapterId
workspaceId
```

These IDs are needed by Member 2 and Member 3.

## Step 2: Member 2 uploads and indexes documents

Member 2 uses:

```text
workspaceId
courseId
chapterId
uploadedBy/userId
```

Then Member 2 provides:

```text
documentId
chunkId
retrieval data
```

These are needed by Member 3 for chatbot and citations.

## Step 3: Member 3 builds chatbot and evaluation

Member 3 uses:

```text
workspaceId
document_chunks
retrieval_results
chatSessionId
datasetId
experimentId
```

Then Member 3 provides:

```text
chat answer
citations
chat history
benchmark results
fine-tuning experiment record
```

---

# 4. Suggested Development Order

## Phase 1: Basic system

```text
Member 1:
- Register
- Login
- Course
- Chapter
- Workspace

Member 2:
- Document entities
- Document repositories
- Upload document API skeleton

Member 3:
- Chat entities
- Evaluation entities
- API skeleton for chat/evaluation
```

## Phase 2: Main features

```text
Member 1:
- Test auth and role flow
- Support FE with user/course/workspace APIs

Member 2:
- Complete upload
- Complete text extraction
- Complete chunking
- Complete embedding demo

Member 3:
- Complete chat session
- Complete ask chatbot
- Complete chat history
- Complete citations
```

## Phase 3: Research module and FE integration

```text
Member 1:
- Final check user/course flow

Member 2:
- Final check document indexing flow

Member 3:
- Complete evaluation dataset
- Complete experiment results
- Complete fine-tuning JSONL export
- Complete API documentation for FE
```

---

# 5. Short Version for Report

The backend team is divided into three members. Member 1 is responsible for authentication and course management, including users, roles, courses, chapters, and course workspaces. Member 2 is responsible for document processing and RAG preparation, including document upload, text extraction, chunking, embedding models, chunk embeddings, retrieval queries, and retrieval results. Member 3 is responsible for chatbot interaction, evaluation, and fine-tuning, including chat sessions, chat messages, citations, saved notes, evaluation datasets, evaluation questions, experiments, experiment results, fine-tuning JSONL export, and API documentation for frontend integration.
