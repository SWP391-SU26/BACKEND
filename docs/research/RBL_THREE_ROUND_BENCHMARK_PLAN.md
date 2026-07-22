# Ke hoach benchmark RBL ba vong

## Muc tieu

Thuc nghiem tach tung bien de chon cau hinh RAG tot nhat truoc khi so sanh voi
`Qwen2.5-0.5B-Instruct + LoRA`. Moi lan chay dung cung test set 50 cau hoi co
ground truth do con nguoi tao, cung checksum, seed `42`, `topK=5` va nguong
`0.25`.

Giai doan hien tai moi trien khai baseline:

- Embedding: `BAAI/bge-m3`.
- Chunking: `PARAGRAPH_700_120`.
- RAGAS: `ragas==0.4.3`, judge `gpt-4o-mini`, evaluator embedding
  `text-embedding-3-small`.
- Fine-tuned model: `Qwen2.5-0.5B-Instruct + LoRA`.

## Vong 1 - Chon chunking

Giu co dinh BGE-M3 va so sanh:

| Ma index | Strategy | Cau hinh |
|---|---|---|
| `bge-fixed-v1` | Fixed-size | 700 ky tu, overlap 120 |
| `bge-paragraph-v1` | Paragraph-aware | 700 ky tu, overlap 120 |
| `bge-semantic-v1` | Sentence/semantic | Tach cau, gop theo semantic threshold |

Winner duoc chon theo context recall va context precision, sau do moi dung
answer correctness, faithfulness va latency de pha hoa.

## Vong 2 - Chon embedding

Dung strategy thang Vong 1 va tao index rieng cho:

| Ma index | Embedding | Revision bat buoc |
|---|---|---|
| `best-bge-v1` | `BAAI/bge-m3` | Hugging Face commit SHA |
| `best-e5-v1` | `intfloat/multilingual-e5-base` | Hugging Face commit SHA |
| `best-phobert-v1` | `vinai/phobert-base-v2` | Hugging Face commit SHA |

PhoBERT khong phai sentence embedding model san. Vong nay phai cong bo pooling,
normalization va truy van prefix; neu khong thi ket qua khong du dieu kien so
sanh.

## Vong 3 - RAG voi Fine-tuning

So sanh cau hinh RAG thang Vong 2 voi LoRA tren cung dataset checksum. RAG luu
day du context va nam metric RAGAS. Fine-tuned chi dung answer relevancy va
answer correctness; metric context de `null`, khong gan diem gia.

Dataset benchmark phai co `0` cau trung train/validation cua LoRA. Adapter
checksum, base-model revision va generation parameters phai duoc dong bang.

## Ma tran bien va index isolation

Moi experiment co mot `index_profile_id` bat bien, gom:

- `chunking_strategy`, chunk size, overlap va parser version.
- `embedding_model_id`, model revision, dimension va normalization.
- Danh sach document ID, document checksum va dataset checksum.
- `topK`, threshold, generation model/version, temperature va seed.

Khong ghi de vector cua index cu. Khoa de xuat cho vector la
`(chunk_id, index_profile_id)`. Chat production chi dung profile duoc danh dau
`ACTIVE`; experiment luon tro den profile da dong bang.

## Mo rong schema va API sau nay

Can them `index_profiles`, `document_indexes` va khoa `index_profile_id` vao
`chunk_embeddings`, `experiments`, `retrieval_queries`. Endpoint upload/reindex
nhan profile; retrieval nhan profile; experiment chi duoc Run khi tat ca document
trong snapshot da `INDEXED` bang dung profile.

API can mo rong:

- `POST /api/index-profiles` va `GET /api/index-profiles`.
- `POST /api/documents/{id}/indexes/{profileId}`.
- `GET /api/documents/{id}/indexes`.
- `POST /api/evaluation/experiments` nhan `indexProfileId`.

## RAGAS, chi phi va thoi gian

Luu metric tung cau va aggregate `average`, `min`, `max`, `standard deviation`,
latency, failure count. Luu so request, token judge, token embedding, don gia va
chi phi uoc tinh. Concurrency toi da `2`, retry toi da `3`; rate limit phai la
mot failure co ma loi, khong duoc bo qua.

Moi cau hinh chay it nhat ba lan voi seed da cong bo. Bao cao median va do lech
chuan; neu chi chay mot lan thi ghi ro la ket qua tham do.

## Quy tac chon winner va bao cao

1. Chi so sanh cac run co cung dataset checksum va `OFFICIAL_RAGAS` thanh cong.
2. Loai run co failure rate tren 5% khoi ket luan winner.
3. Cong bo diem tung metric, latency, chi phi; khong gop thanh mot diem tuy y.
4. Neu chenh lech answer correctness duoi 2 diem phan tram, xem la gan tuong
   duong va uu tien grounding, latency, chi phi.
5. Bao cao ca cau hoi tot, cau hoi te va loi; khong chi chon mau co loi.

## Tieu chi nghiem thu

- Moi profile co vector rieng va revision/checksum truy vet duoc.
- 50 cau benchmark khong trung train/validation.
- Moi run co config hash, dataset checksum va model checksum bat bien.
- Dashboard hien `Official RAGAS`, evaluator model, metric tung cau va aggregate.
- Bang ket qua co them latency, chi phi, failure count va y nghia thong ke.
