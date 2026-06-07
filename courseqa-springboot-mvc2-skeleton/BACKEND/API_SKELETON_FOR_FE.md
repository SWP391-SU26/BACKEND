# Member 3 API Skeleton for Frontend

Base URL: `http://localhost:8080`

All JSON endpoints return the common wrapper:

```json
{
  "success": true,
  "message": null,
  "data": {}
}
```

IDs are UUID strings.

## Chat

### Create or Get Session

`POST /api/chat/sessions`

Creates a new active chat session for the user/workspace pair.

Request:

```json
{
  "userId": "00000000-0000-0000-0000-000000000000",
  "workspaceId": "00000000-0000-0000-0000-000000000000"
}
```

Response `data`:

```json
{
  "chatSessionId": "00000000-0000-0000-0000-000000000000",
  "workspaceId": "00000000-0000-0000-0000-000000000000",
  "userId": "00000000-0000-0000-0000-000000000000",
  "isActive": true,
  "startedAt": "2026-06-08T10:00:00"
}
```

### Ask Question

`POST /api/chat/sessions/{sessionId}/ask`

Submits a question to a chat session. This endpoint currently throws `UnsupportedOperationException` until the AI contract is implemented.

Request:

```json
{
  "question": "What is artificial intelligence?"
}
```

Response `data` when implemented:

```json
{
  "messageId": "00000000-0000-0000-0000-000000000000",
  "chatSessionId": "00000000-0000-0000-0000-000000000000",
  "senderRole": "assistant",
  "messageContent": "Answer text",
  "createdAt": "2026-06-08T10:00:00"
}
```

### Get Session History

`GET /api/chat/sessions/{sessionId}/history`

Returns up to 50 chat messages for a session, ordered oldest to newest.

Response `data`:

```json
[
  {
    "messageId": "00000000-0000-0000-0000-000000000000",
    "chatSessionId": "00000000-0000-0000-0000-000000000000",
    "senderRole": "user",
    "messageContent": "Question text",
    "createdAt": "2026-06-08T10:00:00"
  }
]
```

### Save Note

`POST /api/chat/notes`

Saves a user note for a workspace.

Request:

```json
{
  "userId": "00000000-0000-0000-0000-000000000000",
  "workspaceId": "00000000-0000-0000-0000-000000000000",
  "noteTitle": "Important concept",
  "noteContent": "Note body"
}
```

Response `data`:

```json
{
  "noteId": "00000000-0000-0000-0000-000000000000",
  "workspaceId": "00000000-0000-0000-0000-000000000000",
  "userId": "00000000-0000-0000-0000-000000000000",
  "noteTitle": "Important concept",
  "noteContent": "Note body",
  "createdAt": "2026-06-08T10:00:00"
}
```

### Get Workspace Notes

`GET /api/chat/notes/workspace/{workspaceId}`

Returns notes for a workspace, ordered newest first.

Response `data`:

```json
[
  {
    "noteId": "00000000-0000-0000-0000-000000000000",
    "workspaceId": "00000000-0000-0000-0000-000000000000",
    "userId": "00000000-0000-0000-0000-000000000000",
    "noteTitle": "Important concept",
    "noteContent": "Note body",
    "createdAt": "2026-06-08T10:00:00"
  }
]
```

## Evaluation

### List Datasets

`GET /api/evaluation/datasets`

Returns all evaluation datasets.

Response `data`:

```json
[
  {
    "datasetId": "00000000-0000-0000-0000-000000000000",
    "datasetName": "AI101 benchmark",
    "courseId": "00000000-0000-0000-0000-000000000000",
    "workspaceId": "00000000-0000-0000-0000-000000000000",
    "createdBy": "00000000-0000-0000-0000-000000000000",
    "createdAt": "2026-06-08T10:00:00"
  }
]
```

### Create Dataset

`POST /api/evaluation/datasets`

Creates an evaluation dataset.

Request:

```json
{
  "datasetName": "AI101 benchmark",
  "courseId": "00000000-0000-0000-0000-000000000000",
  "workspaceId": "00000000-0000-0000-0000-000000000000",
  "createdBy": "00000000-0000-0000-0000-000000000000"
}
```

Response `data`: an `EvaluationDataset` object.

### Add Question

`POST /api/evaluation/questions`

Adds a question to a dataset.

Request:

```json
{
  "datasetId": "00000000-0000-0000-0000-000000000000",
  "questionText": "What is AI?",
  "groundTruthAnswer": "AI is artificial intelligence."
}
```

Response `data`: an `EvaluationQuestion` object.

### Get Dataset Questions

`GET /api/evaluation/datasets/{datasetId}/questions`

Returns all questions for a dataset.

Response `data`:

```json
[
  {
    "evaluationQuestionId": "00000000-0000-0000-0000-000000000000",
    "datasetId": "00000000-0000-0000-0000-000000000000",
    "questionText": "What is AI?",
    "groundTruthAnswer": "AI is artificial intelligence."
  }
]
```

### Create Experiment

`POST /api/evaluation/experiments`

Creates an evaluation experiment record with default status `PENDING`.

Request:

```json
{
  "experimentName": "RAG baseline",
  "experimentType": "RAG",
  "configJson": "{}",
  "createdBy": "00000000-0000-0000-0000-000000000000"
}
```

Response `data`: an `Experiment` object.

### List Experiments

`GET /api/evaluation/experiments`

Returns all experiments.

Response `data`: array of `Experiment` objects.

### Run Benchmark

`POST /api/evaluation/experiments/{experimentId}/run`

Runs a benchmark. This endpoint currently throws `UnsupportedOperationException` until the AI benchmark contract is implemented.

Response `data` when implemented:

```json
"Benchmark started"
```

### Get Experiment Results

`GET /api/evaluation/experiments/{experimentId}/results`

Returns results for an experiment.

Response `data`:

```json
[
  {
    "experimentResultId": "00000000-0000-0000-0000-000000000000",
    "experimentId": "00000000-0000-0000-0000-000000000000",
    "evaluationQuestionId": "00000000-0000-0000-0000-000000000000",
    "generatedAnswer": "Answer text",
    "faithfulness": 0.9,
    "answerRelevance": 0.8,
    "contextPrecision": 0.7,
    "contextRecall": 0.6,
    "latencyMs": 1200,
    "cost": 0.001
  }
]
```

## Fine-Tuning

### Export JSONL

`POST /api/fine-tuning/export-jsonl/{datasetId}`

Exports a dataset as a downloadable JSONL file with `prompt` and `completion` fields.

Response: file download with `Content-Disposition: attachment`.

### List Experiment Files

`GET /api/fine-tuning/files`

Returns generated file names for experiment records.

Response `data`:

```json
[
  "experiment_00000000-0000-0000-0000-000000000000_RAG baseline.jsonl"
]
```

### Create Fine-Tuning Experiment

`POST /api/fine-tuning/experiments`

Creates a fine-tuning experiment record with default status `PENDING`.

Request:

```json
{
  "name": "Fine tuning run",
  "researcherId": "00000000-0000-0000-0000-000000000000",
  "configJson": "{}"
}
```

Response `data`: an `Experiment` object.
