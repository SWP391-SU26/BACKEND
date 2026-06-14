# Chatbot RAG hỏi đáp tài liệu môn học

MVP này phục vụ phần chatbot của dự án: upload tài liệu môn học, tự động extract text, chunk, embedding, lưu SQLite, hỏi đáp theo tài liệu, trích dẫn nguồn và từ chối câu hỏi ngoài phạm vi.

## Chức năng đã có

- Upload và index `PDF`, `DOCX`, `PPTX`, `TXT`, `MD`.
- Tách tài liệu thành chunks có metadata môn học, chương, file, trang/slide.
- Embedding mặc định sử dụng `BAAI/bge-m3`, có fallback đọc model từ cache khi mất mạng.
- Có thể đổi sang `local-hash-v1` hoặc `text-embedding-3-small`.
- Chat theo phiên, lưu lịch sử hội thoại trong SQLite.
- Trả lời có nguồn tham chiếu.
- Nếu không tìm thấy context đủ liên quan, bot trả lời: `Tôi chưa tìm thấy thông tin này trong tài liệu đã cung cấp.`

## Cấu trúc thư mục

```text
D:\CHATBOT
├── app/
│   └── streamlit_app.py
├── src/
│   ├── config.py
│   ├── document_loader.py
│   ├── chunker.py
│   ├── embeddings.py
│   ├── rag_pipeline.py
│   ├── storage.py
│   └── text_utils.py
├── data/
│   ├── raw/
│   ├── processed/
│   └── db/
├── experiments/
├── reports/
├── tests/
├── requirements.txt
└── .env.example
```

## Chạy ứng dụng

```powershell
cd D:\CHATBOT
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
streamlit run app\streamlit_app.py
```

Nếu chưa có API key, app vẫn chạy ở chế độ offline. Chất lượng retrieval sẽ tốt hơn khi dùng embedding model chuyên dụng.

## Chạy REST API cho frontend Java

```powershell
cd D:\CHATBOT
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

API docs tự động:

```text
http://127.0.0.1:8000/docs
```

Các endpoint chính:

```text
GET    /api/health
GET    /api/documents
GET    /api/subjects
POST   /api/documents
DELETE /api/documents/{document_id}
GET    /api/sessions
POST   /api/sessions
GET    /api/sessions/{session_id}/messages
POST   /api/chat
GET    /api/benchmarks
GET    /api/benchmarks/{run_id}
POST   /api/benchmarks/run
POST   /api/benchmark-jobs
GET    /api/benchmark-jobs
GET    /api/benchmark-jobs/{job_id}
GET    /api/dashboard/summary
GET    /api/dashboard/comparison
GET    /api/evaluation/capabilities
```

Ví dụ upload tài liệu từ frontend Java cần gửi `multipart/form-data`:

```text
file=<PDF/DOCX/PPTX/TXT/MD>
subject=Môn học demo
chapter=Chương 1
```

Ví dụ body khi chat:

```json
{
  "session_id": "optional-session-id",
  "question": "RAG là gì?",
  "subject": "Software Testing"
}
```

Response chat trả về:

```json
{
  "session_id": "...",
  "answer": "...",
  "sources": [],
  "retrieved": []
}
```

File mẫu gọi API bằng Java nằm ở:

```text
examples/JavaApiClient.java
```

## Benchmark RAG

Test set nằm tại `data/test_set.csv`. Mỗi dòng sử dụng các cột:

```text
question,expected_answer,expected_source,expected_page,subject,is_out_of_scope,category
```

Chạy benchmark bằng dòng lệnh:

```powershell
python experiments\run_benchmark.py --test-set data\test_set.csv
```

Hoặc gọi API:

```http
POST /api/benchmarks/run
Content-Type: application/json

{
  "test_set_path": "data/test_set.csv"
}
```

Kết quả gồm:

- `answer_token_f1`: độ trùng khớp token với ground truth.
- `source_hit_rate`: tỷ lệ lấy đúng tài liệu nguồn.
- `page_hit_rate`: tỷ lệ lấy đúng trang tài liệu.
- `refusal_accuracy`: tỷ lệ từ chối đúng câu ngoài tài liệu.
- `average_top_retrieval_score`: điểm retrieval trung bình.
- `average_latency_ms`: thời gian phản hồi trung bình.
- `faithfulness_proxy`: tỷ lệ token câu trả lời được hỗ trợ bởi context.
- `answer_relevancy_proxy`: mức trùng khớp với ground truth.
- `context_precision_proxy`: tỷ lệ chunks có chứa thông tin ground truth.
- `context_recall_proxy`: tỷ lệ thông tin ground truth xuất hiện trong context.

Bốn metric có hậu tố `_proxy` là đánh giá local không sử dụng LLM judge và không
phải RAGAS chính thức.

Retrieval mặc định sử dụng hybrid search: cosine similarity từ embedding kết hợp
keyword matching. Trọng số semantic được cấu hình bằng `SEMANTIC_WEIGHT`.

Chạy sweep để tìm cấu hình retrieval tốt nhất:

```powershell
python experiments\run_retrieval_sweep.py
```

Kết quả tổng hợp được lưu tại `reports/retrieval_sweep.csv`.

Index và benchmark bằng `bge-m3`:

```powershell
python experiments\index_documents.py --embedding-provider sentence-transformers --embedding-model BAAI/bge-m3
python experiments\run_benchmark.py --embedding-provider sentence-transformers --embedding-model BAAI/bge-m3
python experiments\run_retrieval_sweep.py --embedding-provider sentence-transformers --embedding-model BAAI/bge-m3
```

Chi tiết kết quả được lưu trong SQLite và xuất ra `reports/benchmark_<run_id>.csv`.

## Fine-tuning baseline bằng LoRA/QLoRA

Fine-tuning được tách khỏi dependencies chatbot để tránh làm môi trường chạy API quá nặng.

Đầu tiên, hoàn thiện `data/test_set.csv` với tối thiểu 50 cặp câu hỏi và câu trả lời. Sau đó tạo dataset:

```powershell
python experiments\prepare_finetuning_data.py --source data\test_set.csv
```

Kết quả:

```text
data/finetuning/train.jsonl
data/finetuning/validation.jsonl
data/finetuning/dataset_summary.json
```

Kiểm tra cấu hình và dataset mà chưa train:

```powershell
python experiments\train_lora.py --dry-run
```

Cài dependencies fine-tuning:

```powershell
python -m pip install -r requirements-finetuning.txt
```

Chạy LoRA/QLoRA:

```powershell
python experiments\train_lora.py --config experiments\lora_config.json
```

Cấu hình mặc định sử dụng `Qwen/Qwen2.5-0.5B-Instruct` để làm baseline nhỏ. QLoRA cần CUDA GPU và `bitsandbytes`. Nếu không có GPU, có thể đặt `use_qlora` thành `false`, nhưng quá trình LoRA trên CPU vẫn rất chậm.

API hỗ trợ frontend:

```text
POST /api/finetuning/prepare
GET  /api/finetuning/status
```

## Local LoRA inference

Backend mặc định dùng `RAG + Qwen LoRA` nếu adapter tồn tại, và tự fallback về
extractive answer nếu model không load được.

```text
GET /api/model/status
```

Các chế độ generation qua biến môi trường:

```env
GENERATION_PROVIDER=auto
# auto | lora | extractive | openai
```

Benchmark riêng từng chế độ:

```powershell
python experiments\run_benchmark.py --generation-provider extractive
python experiments\run_benchmark.py --generation-provider lora
python experiments\run_benchmark.py --generation-provider lora --mode finetuned_only
```

Vì benchmark LoRA trên CPU chạy lâu, frontend nên dùng:

```http
POST /api/benchmark-jobs
Content-Type: application/json

{
  "test_set_path": "data/test_set.csv",
  "mode": "rag",
  "generation_provider": "lora"
}
```

Sau đó polling `GET /api/benchmark-jobs/{job_id}` cho đến khi trạng thái là
`completed` hoặc `failed`. Dữ liệu biểu đồ tổng hợp lấy từ
`GET /api/dashboard/summary`.

Body tạo dataset:

```json
{
  "source_csv": "data/test_set.csv",
  "validation_ratio": 0.2,
  "seed": 42
}
```

## Cấu hình OpenAI

Tạo file `.env` hoặc set biến môi trường:

```powershell
$env:OPENAI_API_KEY="sk-..."
$env:OPENAI_CHAT_MODEL="gpt-4o-mini"
$env:EMBEDDING_PROVIDER="openai"
$env:EMBEDDING_MODEL="text-embedding-3-small"
streamlit run app\streamlit_app.py
```

## Cấu hình sentence-transformers

```powershell
pip install sentence-transformers
$env:EMBEDDING_PROVIDER="sentence-transformers"
$env:EMBEDDING_MODEL="BAAI/bge-m3"
streamlit run app\streamlit_app.py
```

## Gợi ý dùng cho demo

1. Upload 3-5 tài liệu của một môn học.
2. Hỏi câu có trong tài liệu để kiểm tra citation.
3. Hỏi câu ngoài tài liệu để kiểm tra cơ chế từ chối.
4. Chụp màn hình tab Chat và tab Tài liệu cho báo cáo.
