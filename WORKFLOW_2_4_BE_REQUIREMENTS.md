# Backend Requirements - Workflow 2 and Workflow 4

## Objective

Complete the backend behavior required by the Course/Workspace and RAG Chat workflows while keeping the frontend-facing chat endpoint stable:

```http
POST /api/chat/sessions/{sessionId}/ask
```

The current frontend is integrated with the existing endpoint. Internal Java-to-Python contracts may change without requiring another frontend change.

## Current Risks

1. Java stores course documents and chunks in SQL Server, while Python retrieves from its own SQLite database.
2. `ChatService` sends only `question` to Python and sets both `session_id` and `subject` to `null`.
3. Python therefore searches all records in its SQLite corpus and cannot enforce Java workspace isolation.
4. The Java `/ask` flow does not call `RetrievalService`, even though that service can filter chunks by `workspaceId`.
5. Saved citations contain title/page/preview but omit Java `retrievalResultId`, `documentId`, and `chunkId`.
6. A Python AI error can leave a saved user message without a corresponding assistant result.

These issues make the current chat endpoint suitable only for local demonstration. It must not be treated as tenant/workspace isolated.

## Required Architecture

Java and SQL Server must be the source of truth for users, workspaces, sessions, documents, chunks, retrieval results, messages, notes, and citations.

Python must expose a stateless generation operation. Python must not select documents from its SQLite store when called by Java.

Expected flow:

1. Resolve `ChatSession` from `sessionId`.
2. Read `workspaceId` from the persisted session; never accept it from the ask request.
3. Save the user message and obtain `userMessageId`.
4. Execute `RetrievalService` with `chatSessionId`, `userMessageId`, session workspace, selected embedding model, and question.
5. If no result passes the threshold, save the standard out-of-context assistant response and return an empty citation list.
6. If context exists, call Python `POST /api/generate` with the retrieved Java chunks.
7. Save the assistant message.
8. Save citations using the exact Java retrieval/document/chunk identifiers.
9. Return the existing `AskResponse` shape to the frontend.

## Python Internal API

### Request

```http
POST http://localhost:8001/api/generate
Content-Type: application/json
```

```json
{
  "question": "What is an intelligent agent?",
  "contexts": [
    {
      "retrievalResultId": "32b0c2b7-a953-497f-b7e1-a34600e6c108",
      "documentId": "d4d24883-e585-4d46-a485-19a16980af46",
      "chunkId": "fb0c9f04-d0d0-4836-b1d1-26e19f16df89",
      "documentTitle": "AI Foundations",
      "pageStart": 6,
      "pageEnd": 6,
      "content": "An intelligent agent receives percepts...",
      "score": 0.82
    }
  ]
}
```

Validation:

- `question` is required and non-blank.
- `contexts` is required and limited to the Java-selected top-K results.
- Context content must not be loaded or replaced from Python SQLite.
- Generation must be grounded only in the supplied contexts.

### Response

```json
{
  "answer": "An intelligent agent receives percepts and acts through actuators.",
  "sources": [
    {
      "retrievalResultId": "32b0c2b7-a953-497f-b7e1-a34600e6c108",
      "documentId": "d4d24883-e585-4d46-a485-19a16980af46",
      "chunkId": "fb0c9f04-d0d0-4836-b1d1-26e19f16df89",
      "documentTitle": "AI Foundations",
      "pageStart": 6,
      "pageEnd": 6,
      "quoteText": "An intelligent agent receives percepts...",
      "score": 0.82
    }
  ],
  "model": "configured-model",
  "latencyMs": 1240
}
```

Python must preserve the supplied IDs. It must not invent source IDs or return a source that was not included in `contexts`.

## Stable Frontend API

### Ask question

```http
POST /api/chat/sessions/{sessionId}/ask
```

```json
{
  "question": "What is an intelligent agent?"
}
```

Existing response fields must remain available:

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "chatSessionId": "...",
    "userMessageId": "...",
    "assistantMessageId": "...",
    "answer": "...",
    "citations": [
      {
        "documentTitle": "AI Foundations",
        "pageStart": 6,
        "pageEnd": 6,
        "quoteText": "..."
      }
    ]
  }
}
```

The backend may add `documentId`, `chunkId`, and `retrievalResultId` to citation items. Existing fields must not be removed.

### Error behavior

- Missing session: `404`.
- Blank question: `400`.
- Python unavailable/timeout: `503` with a stable message suitable for retry.
- No relevant context: successful response containing the configured out-of-context answer and `citations: []`.
- AI failure must not leave an unexplained orphan user message. Either roll back the message unit of work or persist an explicit failed/error state that history can represent.

## Workflow 2 Hardening

The existing six course/chapter/workspace APIs cover the required workflow, but the following controls are required before production:

1. Require an authenticated Admin or Teacher for create operations.
2. Derive `createdBy` and `ownerUserId` from the authenticated principal; do not trust arbitrary UUIDs from request bodies.
3. Reject duplicate normalized course codes with `409 Conflict`.
4. Validate workspace visibility against the supported enum (`COURSE`, `PRIVATE`, `PUBLIC`) and return `400` for invalid values.
5. Restrict list/detail results to courses and workspaces visible to the authenticated user.
6. Return a consistent `{ success, message, data }` error contract.

## Acceptance Criteria

### Workspace isolation

- Create workspace A and workspace B with different documents.
- Ask a question in session A whose answer exists only in workspace B.
- Response must be out-of-context with no citation from workspace B.
- Retrieval queries/results must contain only workspace A IDs.

### Grounded answer

- Ask a question with at least one matching SQL Server chunk.
- Java must persist retrieval query/results, assistant message, and citations.
- Every citation ID must refer to the same Java document/chunk included in the Python request.

### No context

- Ask a question with no chunk above threshold.
- Python generation must not be called.
- A deterministic out-of-context assistant message must be saved and returned with no citations.

### AI failure

- Stop Python or force a timeout.
- Java returns `503` and the request can be retried.
- History contains no unexplained half-completed exchange.

### Session continuity

- Multiple asks using one Java session remain associated with that session.
- Python does not create a separate SQLite session for Java-originated generation.

### Workflow 2

- Admin/Teacher can create course, chapter, and workspace.
- Unauthorized users receive `403`.
- Duplicate course code receives `409`.
- Invalid visibility receives `400`.

## Required Tests

- Java service tests for session-derived workspace retrieval and no-context behavior.
- Java integration test with a stubbed Python `/api/generate` server.
- Java persistence test validating citation foreign keys.
- Python API tests for request validation, source-ID preservation, and context-only generation.
- End-to-end test proving workspace A cannot retrieve workspace B content.
