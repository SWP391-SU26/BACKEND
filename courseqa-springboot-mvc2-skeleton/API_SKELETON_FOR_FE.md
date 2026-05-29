# API Skeleton for Frontend

Base URL:

```text
http://localhost:8080
```

## Auth

```http
POST /api/auth/register
POST /api/auth/login
POST /api/auth/logout/{userId}
GET  /api/auth/users
GET  /api/auth/users/{userId}/roles
```

## Course

```http
GET  /api/courses
POST /api/courses
GET  /api/courses/{courseId}/chapters
POST /api/courses/{courseId}/chapters
GET  /api/courses/{courseId}/workspaces
POST /api/courses/{courseId}/workspaces
```

## Document

```http
POST /api/documents/upload
GET  /api/documents/workspace/{workspaceId}
GET  /api/documents/{documentId}/pages
GET  /api/documents/{documentId}/chunks
```

## Chat

```http
POST /api/chat/sessions
GET  /api/chat/sessions/workspace/{workspaceId}
POST /api/chat/sessions/{sessionId}/ask
GET  /api/chat/sessions/{sessionId}/history
POST /api/chat/notes
GET  /api/chat/notes/workspace/{workspaceId}
```

## RAG

```http
GET  /api/rag/embedding-models
POST /api/rag/embedding-models
GET  /api/rag/retrieval-queries
GET  /api/rag/retrieval-results
GET  /api/rag/citations
```

## Evaluation

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

## Fine-tuning

```http
POST /api/fine-tuning/export-jsonl/{datasetId}
GET  /api/fine-tuning/files
POST /api/fine-tuning/experiments
```
