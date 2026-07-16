# Backend Completion Summary

## Trạng thái

Backend hiện hỗ trợ đầy đủ ingestion, hybrid retrieval, subject filtering, local
BGE-M3 embedding, local Qwen LoRA generation, history, citations, synchronous và
background benchmark jobs, cùng API dữ liệu dashboard.

## So sánh ba cấu hình

| Cấu hình | Answer F1 | Source hit | Page hit | Refusal | Latency |
|---|---:|---:|---:|---:|---:|
| RAG + Extractive | 0.0619 | 1.000 | 0.975 | 1.000 | 335 ms |
| Fine-tuned only | 0.2492 | 0.000 | 0.000 | 0.800 | 7,742 ms |
| RAG + LoRA | 0.2276 | 1.000 | 0.975 | 1.000 | 15,093 ms |

## Kết luận

- Fine-tuned-only đạt Answer F1 cao nhất nhưng không có citation và từ chối câu
  ngoài tài liệu kém hơn.
- RAG + LoRA là cấu hình phù hợp nhất về chất lượng tổng thể cho đề tài.
- RAG + Extractive phù hợp khi cần phản hồi nhanh trên CPU.
- Metric có hậu tố `_proxy` là đánh giá local dựa trên token overlap, không phải
  RAGAS chính thức và có hạn chế với context tiếng Anh/câu trả lời tiếng Việt.
