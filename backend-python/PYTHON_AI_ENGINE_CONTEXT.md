# PYTHON AI ENGINE CONTEXT — SU26SWP09
> Paste file này vào Claude (VSCode) mỗi khi bắt đầu phiên làm việc mới.
> Phiên bản: v1.0 — Dành riêng cho Python developer phụ trách AI Engine.

---

## 1. VAI TRÒ CỦA PYTHON TRONG HỆ THỐNG

```
Java Spring Boot (do team khác làm)
        │
        │ HTTP nội bộ — chỉ Java mới gọi được Python
        ▼
>>> PYTHON FASTAPI (bạn phụ trách) <<<
        │
        ├── RAG Pipeline      → Qdrant (GCP VM) + LLM API
        ├── Fine-tuned Pipeline → HuggingFace Inference API
        ├── Evaluator         → So sánh 2 answer, chọn winner
        └── RAGAS Benchmark   → Đánh giá định lượng
```

**Nguyên tắc cốt lõi:**
- Python KHÔNG biết gì về user, session, auth — Java xử lý hết
- Python chỉ nhận text, trả AI output
- Python KHÔNG kết nối Azure SQL trực tiếp (trừ khi lưu benchmark results)
- Tất cả request vào Python đều từ Java, không từ FE

---

## 2. 5 ENDPOINT PYTHON PHẢI EXPOSE

```
POST /ai/index-document     ← Java gọi sau khi upload file xong
POST /ai/chat               ← Java gọi khi user hỏi (RAG)
POST /ai/chat-finetuned     ← Java gọi song song với /ai/chat
POST /ai/evaluate           ← Java gọi để chọn winner
POST /ai/benchmark          ← Java gọi khi researcher chạy experiment
```

### Contract chi tiết:

**POST /ai/index-document**
```json
// Request từ Java:
{
  "document_id": "uuid-string",
  "chunks": [
    {
      "chunk_index": 0,
      "content": "Nội dung đoạn văn...",
      "source_page": 1,
      "token_count": 120
    }
  ],
  "embedding_model": "bge-m3",
  "collection_name": "e4_bgem3_500_k3"
}

// Response trả về Java:
{
  "collection_name": "e4_bgem3_500_k3",
  "point_ids": ["uuid1", "uuid2"],
  "total_chunks": 42,
  "status": "success"
}
```

**POST /ai/chat**
```json
// Request từ Java:
{
  "question": "Machine learning là gì?",
  "collection_name": "e4_bgem3_500_k3",
  "embedding_model": "bge-m3",
  "top_k": 5,
  "similarity_threshold": 0.72,
  "conversation_history": [
    {"role": "user", "content": "..."},
    {"role": "assistant", "content": "..."}
  ]
}

// Response trả về Java:
{
  "rag_answer": "Machine learning là...",
  "citations": [
    {
      "chunk_id": "uuid",
      "document_id": "uuid",
      "source_page": 3,
      "excerpt": "đoạn trích ngắn...",
      "similarity_score": 0.89
    }
  ],
  "rag_score": 0.85,
  "is_out_of_scope": false,
  "tokens_used": 850,
  "latency_ms": 1200
}
```

**POST /ai/chat-finetuned**
```json
// Request từ Java:
{
  "question": "Machine learning là gì?",
  "conversation_history": []
}

// Response trả về Java:
{
  "finetuned_answer": "Machine learning là...",
  "finetuned_score": 0.78,
  "latency_ms": 2100
}
```

**POST /ai/evaluate**
```json
// Request từ Java:
{
  "question": "Machine learning là gì?",
  "rag_answer": "...",
  "finetuned_answer": "...",
  "rag_score": 0.85,
  "finetuned_score": 0.78
}

// Response trả về Java:
{
  "winner": "rag",          // "rag" | "finetuned" | "tie"
  "scores": {
    "rag": 0.85,
    "finetuned": 0.78
  },
  "reason": "RAG answer has higher semantic relevance"
}
```

**POST /ai/benchmark**
```json
// Request từ Java:
{
  "experiment_id": "uuid",
  "config": {
    "collection_name": "e4_bgem3_500_k3",
    "embedding_model": "bge-m3",
    "top_k": 5,
    "chunk_size": 500,
    "similarity_threshold": 0.72
  },
  "questions": [
    {
      "question_id": "uuid",
      "question": "...",
      "ground_truth": "..."
    }
  ]
}

// Response trả về Java:
{
  "experiment_id": "uuid",
  "results": [
    {
      "question_id": "uuid",
      "generated_answer": "...",
      "faithfulness": 0.92,
      "answer_relevancy": 0.88,
      "context_precision": 0.85,
      "context_recall": 0.79,
      "latency_ms": 1200
    }
  ],
  "summary": {
    "avg_faithfulness": 0.91,
    "avg_answer_relevancy": 0.87,
    "avg_context_precision": 0.84,
    "avg_context_recall": 0.78
  }
}
```

---

## 3. CẤU TRÚC THƯ MỤC PYTHON

```
backend-python/
│
├── app/
│   ├── main.py              ← FastAPI entrypoint, đăng ký 5 routes
│   └── config.py            ← Đọc .env, settings singleton
│
├── src/
│   ├── document_loader.py   ← Parse PDF/DOCX/PPTX → list[dict]
│   ├── chunker.py           ← LangChain splitters, configurable
│   ├── embeddings.py        ← Wrapper 4 models, trả normalized vector
│   ├── vector_store.py      ← Qdrant: upsert, search, delete
│   ├── rag_pipeline.py      ← Orchestrate: embed → search → prompt → LLM
│   ├── finetuned_pipeline.py← Gọi HuggingFace Inference API
│   ├── evaluator.py         ← So sánh 2 answer, chọn winner
│   └── ragas_runner.py      ← RAGAS evaluation batch
│
├── api/
│   ├── index_document.py    ← POST /ai/index-document handler
│   ├── chat.py              ← POST /ai/chat handler
│   ├── chat_finetuned.py    ← POST /ai/chat-finetuned handler
│   ├── evaluate.py          ← POST /ai/evaluate handler
│   └── benchmark.py         ← POST /ai/benchmark handler
│
├── models/
│   ├── requests.py          ← Pydantic request schemas
│   └── responses.py         ← Pydantic response schemas
│
├── experiments/
│   ├── benchmark_chunking.py
│   ├── benchmark_embedding.py
│   └── ragas_eval.py
│
├── data/
│   ├── raw/
│   ├── processed/
│   └── test_set.csv         ← 50 Q&A + ground truth
│
├── notebooks/
│   └── finetune_colab.ipynb
│
├── tests/
│   ├── test_rag_pipeline.py
│   └── test_evaluator.py
│
├── requirements.txt
├── .env
└── .env.example
```

---

## 4. TECH STACK & DEPENDENCIES

```
# Web framework
fastapi==0.115.0
uvicorn==0.30.0
python-multipart==0.0.9

# Vector DB
qdrant-client==1.11.0

# Document parsing
pypdf==4.3.1
python-docx==1.1.2
python-pptx==1.0.2

# Chunking
langchain==0.3.0
langchain-text-splitters==0.3.0

# Embedding models
sentence-transformers==3.1.1
transformers==4.45.0
torch==2.4.1

# LLM APIs
openai==1.51.0
google-generativeai==0.8.2

# Evaluation
ragas==0.1.21
datasets==3.0.1

# Utilities
python-dotenv==1.0.1
httpx==0.27.2
pandas==2.2.3
pydantic==2.9.2
loguru==0.7.2
```

---

## 5. ENVIRONMENT VARIABLES (.env)

```env
# Qdrant (GCP VM)
QDRANT_HOST=your-gcp-vm-external-ip
QDRANT_PORT=6333

# LLM
OPENAI_API_KEY=sk-...
GEMINI_API_KEY=...
LLM_MODEL=gpt-4o-mini

# Embedding default
EMBEDDING_MODEL_DEFAULT=bge-m3

# Fine-tuned model (HuggingFace)
FINETUNED_MODEL_ENDPOINT=https://api-inference.huggingface.co/models/your-org/your-model
HUGGINGFACE_API_KEY=hf_...

# Server
PYTHON_BE_PORT=8001
LOG_LEVEL=INFO
```

---

## 6. CODING CONVENTIONS

- Tất cả endpoints dùng `async def` + `await`
- Mọi function có **type hints** đầy đủ
- Request/Response dùng **Pydantic models** (không dùng dict raw)
- Log bằng **loguru** — không dùng `print()`
- Error: raise `HTTPException` với status code chuẩn
- Naming: **snake_case** cho tất cả

**Response format chuẩn:**
```python
# Thành công
return {"data": result, "error": None}

# Lỗi
raise HTTPException(status_code=400, detail={"code": "RAG-001", "message": "..."})
```

**Error codes:**
```
RAG-001 : Out of scope — câu hỏi không có trong tài liệu
RAG-002 : Qdrant connection failed
RAG-003 : LLM API timeout (> 30s)
RAG-004 : Embedding model load failed
IDX-001 : Collection not found
IDX-002 : Chunk embedding failed
FT-001  : HuggingFace API error
FT-002  : Fine-tuned model timeout
BM-001  : Invalid experiment config
BM-002  : RAGAS evaluation failed
```

---

## 7. THỨ TỰ BUILD

### Tuần 1 — Nền tảng
```
Ngày 1-2: Setup môi trường, FastAPI chạy được, kết nối Qdrant local
Ngày 3:   document_loader.py — parse PDF/DOCX/PPTX
Ngày 4:   chunker.py — LangChain splitters
Ngày 5:   embeddings.py — bge-m3 wrapper
Ngày 6-7: vector_store.py + endpoint /ai/index-document → test với file thật
```

### Tuần 2 — RAG Pipeline
```
Ngày 1-2: rag_pipeline.py — embed query → search Qdrant → build context
Ngày 3:   Prompt engineering cho LLM (tiếng Việt, citation format)
Ngày 4:   Endpoint /ai/chat → test bằng REST Client
Ngày 5-7: Integration test với Java team (họ gọi Python thật)
```

### Tuần 3 — Fine-tuned + Evaluator
```
Ngày 1-2: Chuẩn bị dataset → chạy fine-tuning trên Colab
Ngày 3:   Push model lên HuggingFace
Ngày 4:   finetuned_pipeline.py → endpoint /ai/chat-finetuned
Ngày 5-7: evaluator.py → endpoint /ai/evaluate
```

### Tuần 4 — Benchmark + Polish
```
Ngày 1-3: ragas_runner.py → endpoint /ai/benchmark
Ngày 4-5: Chạy 8 experiments, lưu kết quả
Ngày 6-7: Fix bugs, viết README, chuẩn bị demo
```

---

## 8. PROMPT TEMPLATES CHO CLAUDE TRONG VSCODE

### Khởi động phiên làm việc
```
[Paste toàn bộ file PYTHON_AI_ENGINE_CONTEXT.md này vào trước]

Hôm nay tôi cần làm: [mô tả task]
File cần tạo: [tên file]
```

### Tạo file mới
```
[CONTEXT đã paste]

Tạo file src/[tên file].py

Yêu cầu:
- [mô tả chức năng]
- Dùng async/await
- Type hints đầy đủ
- Log bằng loguru
- Xử lý lỗi với error codes theo convention
```

### Debug lỗi
```
[CONTEXT đã paste]

File: src/[tên file].py
Lỗi:
[paste traceback]

Code hiện tại:
[paste code]

Phân tích nguyên nhân và fix.
```

### Test endpoint với REST Client (VSCode)
```
[CONTEXT đã paste]

Tạo file test.http để test endpoint POST /ai/[endpoint]
Dùng REST Client extension của VSCode.
Dùng đúng request body theo contract trong context.
```

---

## 9. QDRANT COLLECTIONS — 8 EXPERIMENTS

| Collection | Embedding | Chunk size | Top-K |
|---|---|---|---|
| e1_e5base_300_k3 | multilingual-e5-base | 300 | 3 |
| e2_e5base_500_k3 | multilingual-e5-base | 500 | 3 |
| e3_e5base_800_k3 | multilingual-e5-base | 800 | 3 |
| e4_bgem3_500_k3  | bge-m3 | 500 | 3 |
| e5_openai_500_k3 | text-embedding-3-small | 500 | 3 |
| e6_bgem3_500_k5  | bge-m3 | 500 | 5 |
| e7_bgem3_800_k5  | bge-m3 | 800 | 5 |
| e8_phobert_500_k3| PhoBERT | 500 | 3 |

Mỗi collection trong Qdrant lưu vectors của một config embedding riêng.
Khi Java gọi `/ai/chat`, nó truyền `collection_name` để Python biết search ở đâu.
