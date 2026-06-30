# Test Set Summary

## Phân bố

- Tổng số câu: 50
- Software Testing: 20 câu
- Chữ Hán/tiếng Nhật: 20 câu
- Ngoài tài liệu: 10 câu
- Câu trong phạm vi có ground truth: 40/40
- Câu trong phạm vi có nguồn và trang: 40/40

## Mục tiêu đánh giá

- Kiểm tra khả năng truy xuất đúng giữa hai môn học khác nhau.
- Kiểm tra khả năng lấy đúng tài liệu và đúng trang.
- Kiểm tra câu hỏi định nghĩa, liệt kê, so sánh, vận dụng, suy luận, cách đọc và đọc hiểu.
- Kiểm tra khả năng từ chối câu hỏi ngoài tài liệu.

## Benchmark baseline

- Run ID: `f0075197-1a14-46b7-8baa-c944ea52ecff`
- Embedding: `local-hash-v1`
- Tổng câu hỏi: 50
- Answer token F1: 0.0211
- Source hit rate: 0.8000
- Page hit rate: 0.2000
- Refusal accuracy: 0.8000
- Average top retrieval score: 0.2479
- Average latency: 22.69 ms

Baseline này dùng embedding hashing offline nên chủ yếu dùng để xác nhận pipeline. Kết quả thấp ở page hit và answer F1 là cơ sở để so sánh với embedding retrieval chuyên dụng như `bge-m3`.

## Hybrid retrieval sau tối ưu

Đã sweep 36 cấu hình kết hợp semantic weight, threshold và top-k.

- Run ID tốt nhất: `bd1ae8e3-4a93-4d20-b373-c1ceb3aa1b7b`
- Semantic weight: 0.4
- Lexical weight: 0.6
- Threshold: 0.08
- Top-k: 5
- Answer token F1: 0.0531
- Source hit rate: 0.9750
- Page hit rate: 0.6000
- Refusal accuracy: 0.8400
- Average latency: 19.12 ms
- Quality score: 0.6170

## BGE-M3 sau tối ưu

- Run ID tốt nhất: `7199127c-3304-4940-bc23-6119abfa895d`
- Semantic weight: 0.4
- Lexical weight: 0.6
- Threshold: 0.20
- Top-k: 5
- Answer token F1: 0.0617
- Source hit rate: 1.0000
- Page hit rate: 0.9000
- Refusal accuracy: 1.0000
- Average latency CPU: 244.34 ms
- Quality score: 0.7404

So với `local-hash-v1`, BGE-M3 cải thiện rõ độ chính xác truy xuất và khả năng từ chối, đổi lại latency CPU cao hơn.

Tăng top-k từ 5 lên 10 tiếp tục cải thiện:

- Run ID: `aa77fa65-9fd8-4816-a02d-31276a388c45`
- Page hit rate: 0.9750
- Refusal accuracy: 1.0000
- Answer token F1: 0.0619
- Average latency CPU: 274.38 ms
- Quality score: 0.7592

## RAG + Qwen LoRA local

- Run ID: `0aa66855-633b-44c2-a464-0751e9549c37`
- Answer token F1: 0.2276
- Source hit rate: 1.0000
- Page hit rate: 0.9750
- Refusal accuracy: 1.0000
- Average latency CPU: 13458.18 ms
- Quality score không tính latency: 0.8007

LoRA giúp Answer F1 tăng khoảng 3.7 lần so với extractive answer, nhưng inference trên CPU chậm hơn đáng kể.
