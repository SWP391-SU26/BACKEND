# Tài liệu công việc cho AI Team — Lỗi 1 (Re-architecture) & Chunking

> **Người nhận:** Thành viên phụ trách Python AI Engine
> **Người viết:** Khang (Backend)
> **Ngày:** 27/06/2026
> **Phạm vi:** 2 hạng mục — (1) Tái cấu trúc luồng RAG (Lỗi 1), (2) Thay đổi cách chunking

---

## Bối cảnh chung

Hiện tại hệ thống có **2 cơ sở dữ liệu tách biệt** lưu cùng một loại dữ liệu:
- **Java** lưu documents / pages / chunks trong **SQL Server** (qua `DocumentService.java`).
- **Python** lưu documents / chunks / embeddings trong **SQLite** nội bộ (`chatbot.sqlite3`).

Khi sinh viên chat, hiện tại **Python tự retrieval trên SQLite của nó**, hoàn toàn bỏ qua dữ liệu trong SQL Server của Java. Điều này gây ra:
- Mất cách ly workspace (sinh viên workspace A có thể nhận câu trả lời từ tài liệu workspace B).
- Citations không map được về `document_id` / `chunk_id` thật trong SQL Server.

Mục tiêu: **Java là nguồn dữ liệu duy nhất (Source of Truth)**. Java làm retrieval, Python chỉ làm generator.

---

# HẠNG MỤC 1 — Thêm endpoint `/api/generate` (Lỗi 1)

## Mục tiêu
Tạo một endpoint **stateless** trên Python: nhận sẵn câu hỏi + danh sách chunks (do Java tìm từ SQL Server), chỉ việc gọi LLM sinh câu trả lời. **Không tự query SQLite, không tự retrieval.**

## Vị trí code
File: `app/main.py` (thêm endpoint mới, không xóa `/api/chat` cũ).

## Hợp đồng API (API Contract)

### Request — Java gửi sang Python
`POST /api/generate`
```json
{
  "question": "Khái niệm trừu tượng hóa là gì?",
  "contexts": [
    {
      "chunk_id": "<id chunk trong SQL Server của Java>",
      "document_id": "<id document trong SQL Server>",
      "filename": "OOP_Chapter3.pdf",
      "page": 12,
      "content": "Nội dung đầy đủ của chunk này..."
    }
  ]
}
```

### Response — Python trả về cho Java
```json
{
  "answer": "Trừu tượng hóa là...",
  "is_out_of_scope": false,
  "sources": [
    {
      "chunk_id": "<chunk_id Java đã gửi — trả lại nguyên>",
      "document_id": "<document_id Java đã gửi — trả lại nguyên>",
      "filename": "OOP_Chapter3.pdf",
      "page": 12,
      "preview": "280 ký tự đầu của chunk"
    }
  ]
}
```

> **QUAN TRỌNG:** `chunk_id` và `document_id` trong response **phải là chính giá trị Java đã gửi sang** (không phải id của SQLite). Đây là điểm mấu chốt để Java map citations về đúng bản ghi trong SQL Server.

## Logic bên trong endpoint
1. Nhận `question` + `contexts` từ request.
2. Nếu `contexts` rỗng → trả `answer` mặc định + `is_out_of_scope: true` (Java thực ra sẽ tự xử lý trường hợp này trước, nên endpoint chủ yếu nhận khi đã có context).
3. Chuyển `contexts` (dạng JSON) thành các đối tượng `RetrievedChunk` mà pipeline hiện tại đang dùng.
4. Tận dụng hàm có sẵn `pipeline._generate_answer(question, contexts, sources)` trong `rag_pipeline.py` để sinh câu trả lời (không cần viết lại logic gọi LLM).
5. Build `sources` từ chunks đầu vào, **giữ nguyên `chunk_id` / `document_id` của Java**.
6. Trả về theo đúng cấu trúc Response ở trên.

## Lưu ý
- Endpoint này **không** gọi `store.retrieve()`, **không** đụng tới SQLite.
- `/api/chat` cũ giữ nguyên để không làm hỏng các phần đang chạy — chỉ thêm mới `/api/generate`.
- Port vẫn là **8001** như cũ.

---

# HẠNG MỤC 2 — Thay đổi cách Chunking

## Hiện trạng
File: `src/chunker.py`, hàm `chunk_pages()`.
Hiện đang chia theo **cửa sổ trượt 700 từ, overlap 120 từ**, theo từng trang. **Không quan tâm heading / chương / đoạn văn.**

## Yêu cầu từ FE
FE đề xuất chia chunk theo **heading / chương** để mỗi chunk là một chủ đề mạch lạc.

## ⚠️ Vấn đề kỹ thuật cần cân nhắc trước khi làm
File `src/document_loader.py` hiện tại **chỉ trích xuất text thô**, không hề lấy thông tin heading:
- **PDF:** chỉ gọi `extract_text()` — không có thông tin heading/bold/font.
- **DOCX:** gộp toàn bộ vào 1 trang, không có cấu trúc.
- **PPTX:** chỉ lấy text slide thô.

→ Muốn chunk theo heading/chương thật sự thì **phải sửa cả `document_loader.py`** để phát hiện heading, việc này **rất khó và dễ lỗi với PDF/slide tiếng Việt**.

## 3 lựa chọn (đề xuất chọn phương án 2)

### Phương án 1 — Giữ nguyên (700 từ + overlap)
- ✅ Đơn giản, ổn định, chạy được trên mọi loại file.
- ❌ Có thể cắt giữa câu, không theo cấu trúc tài liệu.

### Phương án 2 — Chia theo đoạn văn (ĐỀ XUẤT) ⭐
- Chia theo dấu xuống dòng / đoạn văn trước, sau đó gộp các đoạn lại cho tới khi gần đạt giới hạn ~700 từ.
- ✅ Tôn trọng ranh giới tự nhiên của văn bản, không cắt giữa câu.
- ✅ Chạy được trên mọi định dạng (PDF/DOCX/PPTX) vì không cần phát hiện heading.
- ⚠️ Cần sửa `chunk_pages()` nhưng **không cần** sửa `document_loader.py`.
- Đây là điểm cân bằng tốt nhất giữa chất lượng và độ an toàn.

### Phương án 3 — Chia theo heading/chương (FE đề xuất)
- ✅ Chất lượng retrieval tốt nhất NẾU chạy được.
- ❌ Phải viết lại `document_loader.py` để phát hiện heading (font, bold, vị trí) — rất dễ vỡ với PDF và slide.
- ❌ Rủi ro cao với tài liệu tiếng Việt định dạng không nhất quán.

## Gợi ý logic cho Phương án 2 (paragraph-aware)
Trong `chunk_pages()`, thay vì `split()` toàn bộ thành từ ngay:
1. Tách `page.text` theo đoạn (split theo `\n\n` hoặc dòng trống).
2. Gộp lần lượt các đoạn vào chunk hiện tại, cộng dồn số từ.
3. Khi chunk hiện tại sắp vượt `chunk_size` (700 từ) → chốt chunk, mở chunk mới.
4. Vẫn giữ `page` và `chunk_index` cho mỗi chunk như hiện tại.
5. (Tuỳ chọn) giữ lại overlap nhẹ bằng cách lặp lại đoạn cuối của chunk trước.

## Lưu ý quan trọng về thứ tự ưu tiên
Vì chunking gắn liền với Hạng mục 1: **chunk nào được dùng để trả lời phụ thuộc vào việc retrieval xảy ra ở đâu.**
- Hiện tại retrieval ở Python → đổi chunking ở Python sẽ ảnh hưởng ngay.
- Sau khi làm Hạng mục 1 (Java retrieval) → chunking quan trọng sẽ là **chunking phía Java** (`DocumentService.java`).

→ **Khuyến nghị:** quyết định Hạng mục 1 trước, rồi mới chốt sẽ đổi chunking ở đâu (Java hay Python), để tránh làm 2 lần.

---

# Tóm tắt việc cần làm

| # | Việc | File | Độ ưu tiên |
|---|---|---|---|
| 1 | Thêm endpoint `/api/generate` stateless | `app/main.py` | Cao (cần cho Lỗi 1) |
| 2 | Quyết định phương án chunking (đề xuất P2) | `src/chunker.py` | Trung bình |
| 3 | (Nếu chọn P3) Sửa loader phát hiện heading | `src/document_loader.py` | Thấp / rủi ro cao |

## Cần thống nhất cả nhóm trước khi code
- Hạng mục 1 cần **Java + Python phối hợp cùng lúc** (Java thêm retrieval, Python thêm `/api/generate`). Không làm lẻ một bên được.
- Chốt phương án chunking (1, 2 hay 3) trong buổi họp nhóm.

---

# HẠNG MỤC 3 — So sánh RAG vs Fine-tuning (FE hỏi)

## Câu hỏi từ FE
"Hệ thống có API so sánh RAG vs Fine-tuning không?"

## Hiện trạng kiểm tra trong code

### Phía Java — có method nhưng trỏ vào endpoint KHÔNG tồn tại
File `AIClientService.java` có 2 method nhưng **chính code đã ghi chú là chưa có bên Python**:
- `callChatFinetuned()` → gọi `/ai/chat-finetuned` — **Python chưa có endpoint này**
- `callEvaluate()` → gọi `/ai/evaluate` — **Python chưa có endpoint này**

→ Nếu gọi 2 method này sẽ **lỗi** vì Python không có endpoint tương ứng.

### Phía Python — so sánh chạy qua BENCHMARK, không phải chat trực tiếp
Python **không** có `/ai/chat-finetuned` hay `/ai/evaluate`. Thay vào đó việc so sánh RAG vs Fine-tuning được thiết kế chạy qua hệ thống benchmark:
- `BenchmarkRequest` có field `mode` (mặc định `"rag"`) — có thể đổi giữa rag / fine-tuned.
- `POST /api/benchmarks/run` — chạy benchmark.
- `GET /api/dashboard/comparison` — tổng hợp kết quả theo `benchmark_mode` + `generation_provider`.

## Kết luận trả lời FE

| Loại so sánh | Có không? | Cơ chế |
|---|---|---|
| Chat trực tiếp 2 cột (1 câu hỏi → vừa câu trả lời RAG vừa Fine-tuned) | ❌ KHÔNG | Method Java trỏ vào endpoint Python chưa build |
| So sánh theo lô (chạy test set qua 2 mode, so metrics trên dashboard) | ✅ CÓ (một phần) | `/api/benchmarks/run` + `/api/dashboard/comparison` |

→ **FE không nên build UI dạng chat 2 cột "RAG vs Fine-tuned" trực tiếp** vì backend đó chưa tồn tại. Nên build quanh **benchmark + dashboard comparison**.

## ⚠️ Cảnh báo quan trọng về đánh giá (Evaluation)
Code Python tự ghi rõ:
- `"official_ragas_enabled": False`
- "Dự án không sử dụng API trả phí hoặc LLM judge đủ mạnh để chạy RAGAS chính thức."

→ Hệ thống **không chạy RAGAS chính thức**, mà dùng metric proxy local (`context_recall_proxy`). Chất lượng đánh giá là **gần đúng**, không phải benchmark RAGAS chuẩn. Cần lưu ý khi giảng viên hỏi.

## Việc cần quyết định cho nhóm
1. Có build endpoint `/ai/chat-finetuned` (chat trực tiếp bằng model fine-tuned) không? — nếu có thì đây là việc của AI team trong `app/main.py`.
2. Có build endpoint `/ai/evaluate` (LLM-as-judge so sánh 2 câu trả lời) không? — cần model judge đủ mạnh / API trả phí.
3. Nếu KHÔNG build 2 cái trên → nên **xóa hoặc đánh dấu rõ** 2 method `callChatFinetuned` và `callEvaluate` trong Java để tránh nhầm lẫn là chúng hoạt động.

## Tóm tắt bổ sung bảng việc

| # | Việc | File | Độ ưu tiên |
|---|---|---|---|
| 4 | Quyết định có build `/ai/chat-finetuned` không | `app/main.py` | Cần nhóm chốt |
| 5 | Quyết định có build `/ai/evaluate` không | `app/main.py` | Cần nhóm chốt |
| 6 | Nếu không build → dọn 2 method chết bên Java | `AIClientService.java` | Thấp |
