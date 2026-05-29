# Backend Work Division - 4 Members

## Member 1: Auth + Course

Folders/files:
- `controller/AuthController.java`
- `controller/CourseController.java`
- `service/AuthService.java`
- `service/CourseService.java`
- `model/entity/User.java`
- `model/entity/UserRole.java`
- `model/entity/Course.java`
- `model/entity/Chapter.java`
- `model/entity/CourseWorkspace.java`

APIs:
- `/api/auth`
- `/api/courses`

## Member 2: Document Processing

Folders/files:
- `controller/DocumentController.java`
- `service/DocumentService.java`
- `model/entity/CourseDocument.java`
- `model/entity/DocumentPage.java`
- `model/entity/DocumentChunk.java`

APIs:
- `/api/documents`

## Member 3: RAG + Chat

Folders/files:
- `controller/RagController.java`
- `controller/ChatController.java`
- `service/EmbeddingService.java`
- `service/RetrievalService.java`
- `service/ChatService.java`
- `service/NoteService.java`
- `model/entity/EmbeddingModel.java`
- `model/entity/ChunkEmbedding.java`
- `model/entity/RetrievalQuery.java`
- `model/entity/RetrievalResult.java`
- `model/entity/AnswerCitation.java`
- `model/entity/ChatSession.java`
- `model/entity/ChatMessage.java`
- `model/entity/SavedNote.java`

APIs:
- `/api/rag`
- `/api/chat`

## Member 4: Evaluation + Fine-tuning + API Docs

Folders/files:
- `controller/EvaluationController.java`
- `controller/FineTuningController.java`
- `service/EvaluationService.java`
- `service/FineTuningService.java`
- `model/entity/EvaluationDataset.java`
- `model/entity/EvaluationQuestion.java`
- `model/entity/Experiment.java`
- `model/entity/ExperimentResult.java`
- `data/fine_tuning/courseqa_train.jsonl`

APIs:
- `/api/evaluation`
- `/api/fine-tuning`
