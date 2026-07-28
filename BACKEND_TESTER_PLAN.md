# BACKEND TESTER PLAN

## 1. Muc tieu tai lieu

Tai lieu nay la checklist chinh thuc de kiem tra, sua loi va nghiem thu hai Backend cua du an FStu theo de tai SWP:

> Xay dung chatbot cho phep sinh vien hoi dap dua tren tai lieu mon hoc, dong thoi nghien cuu va so sanh hieu qua giua RAG va fine-tuning trong boi canh tieng Viet.

Pham vi bao gom:

- Java Spring Boot Backend va SQL Server.
- Python AI/RAG Backend.
- Quan ly tai lieu, chat, citation va lich su hoi thoai.
- Embedding, retrieval, fine-tuning, benchmark va RAGAS.
- API phuc vu Frontend va Research Dashboard.
- Bao mat, migration, automated tests va kha nang tai lap ket qua.

## 2. Quyet dinh nghiep vu

- Test set khong bat buoc thuoc MLN121.
- Test set co the thuoc Software Testing, AI101 hoac mon hoc khac.
- Moi test set phai lien ket voi mot workspace va tap tai lieu nguon tuong ung.
- Khong benchmark cau hoi Software Testing tren tai lieu Triet hoc hoac corpus khong lien quan.
- Admin va User da dang nhap deu duoc upload tai lieu.
- Tai lieu upload duoc dung chung trong workspace.
- User duoc sua, re-index va xoa tai lieu do chinh minh upload.
- Admin duoc quan ly toan bo tai lieu.
- User khac trong workspace chi duoc xem va chat, khong duoc sua/xoa tai lieu cua nguoi khac.
- SQL Server la nguon du lieu chinh.
- Python la AI worker cho embedding, generation, fine-tuning va evaluation.

## 3. P0 - Phan quyen va bao mat

### BE-AUTH-002: Token dang nhap chi la UUID user

**Hien trang**

Bearer token co the bi gia bang UUID cua user khac. Nhieu API con nhan `requesterId`, `createdBy` hoac `uploadedBy` tu client.

**Cach sua**

- Them Spring Security va JWT co chu ky.
- JWT chua `sub`, roles, `issuedAt` va `expiry`.
- API lay user tu security principal.
- Xoa cac identity field co the suy ra tu JWT khoi request.
- Thiet lap thoi gian het han va refresh/login policy phu hop ban demo.

**Test cases**

- Token gia, sai chu ky hoac het han: tra `401`.
- Student goi API Admin: tra `403`.
- Thay UUID trong query khong the doc du lieu nguoi khac.

### BE-AUTH-003: API user va role chua duoc bao ve

**Cach sua**

- Chi Admin truy cap danh sach user, role va xoa user.
- Khong tra password hash, internal token hoac permission JSON nhay cam.
- Chan Admin tu xoa chinh minh.
- Them audit cho thay doi role, vo hieu hoa va xoa tai khoan.

## 4. P0 - Quan ly tai lieu

### BE-DOC-002: Chua chuan hoa quyen upload

**Cach sua**

- Admin va User da dang nhap deu duoc upload.
- Server tu lay `uploadedBy` tu JWT.
- User phai chon workspace hop le va co quyen truy cap.
- Kiem tra extension, MIME type, kich thuoc va filename.
- Ho tro chinh thuc PDF, DOCX va PPTX.
- TXT chi duoc giu neu nhom xac nhan can cho demo.

### BE-DOC-003: Indexing xu ly dong bo

**Nguy co**

PDF lon co the timeout; request bi dong trong khi trang thai xu ly khong ro rang.

**Cach sua**

- Upload file va tao `indexing_job`.
- Worker xu ly cac stage `UPLOADED`, `EXTRACTING`, `CHUNKING`, `EMBEDDING`, `INDEXED`, `FAILED`.
- Luu progress, `startedAt`, `completedAt`, error code va error message.
- API upload tra `202 Accepted` cung job ID.
- Them API xem trang thai va retry indexing job.

### BE-DOC-004: Xoa tai lieu co the de lai du lieu lien quan

**Cach sua**

- Transaction xoa citations, retrieval results, embeddings, chunks, pages va document.
- Chi xoa Cloudinary asset sau khi DB transaction thanh cong.
- Neu Cloudinary cleanup that bai, tao cleanup job thay vi khoi phuc document da xoa.

### BE-DOC-005: Trang thai Indexed chua phan anh embedding that

**Cach sua**

- Chi dat `INDEXED` khi so chunk co embedding bang tong chunk cho model active.
- API tra `chunkCount`, `embeddedChunkCount`, `embeddingModel` va `indexingStatus`.

## 5. P0 - Chat va lich su hoi thoai

### BE-CHAT-002: Chat UI bi khoa du retrieval co du lieu

**Nguyen nhan Backend**

Document API khong tra tai lieu dung chung, trong khi RetrievalService lai doc tat ca chunk cua workspace.

**Cach sua**

- Dong nhat quyen doc giua document list, retrieval va citation.
- Chi cho chat khi workspace co it nhat mot document `INDEXED` ma user co quyen doc.

### BE-CHAT-003: Khong gioi han tra loi trong tai lieu

**Hien trang**

Cau hoi ve World Cup van nhan cau tra loi Triet hoc va citation khong lien quan.

**Nguyen nhan**

- Retrieval dang dung keyword cosine.
- Threshold `0.05` qua thap.
- Khong co minimum evidence hoac out-of-scope gate.
- Generator luon co gang tao answer neu nhan bat ky chunk nao.

**Cach sua**

- Dung semantic embedding that ket hop lexical retrieval.
- Calibration threshold tren tap in-scope va out-of-scope.
- Them minimum top score va minimum score margin.
- Neu khong du evidence, khong goi generator va tra `OUT_OF_SCOPE`.
- Prompt Python bat buoc chi dung context.
- Kiem tra answer-evidence truoc khi tra ket qua.

**Test cases**

- Cau thuoc tai lieu tra answer va citation.
- Cau thoi tiet, bong da, gia co phieu phai tu choi.
- Cau khong co evidence khong duoc gan citation.

### BE-CHAT-004: Chua ho tro ngu canh hoi thoai that su

**Cach sua**

- Lay 5-10 message gan nhat.
- Rewrite follow-up question thanh standalone query.
- Gui history da gioi han token sang Python.
- Khong dua message cua session/workspace khac vao context.

### BE-CHAT-005: Citation chua duoc xac thuc

**Hien trang**

Java co the luu cac top chunks ngay ca khi generator khong dung chung.

**Cach sua**

- Python tra `used_chunk_ids`.
- Java xac minh chunk thuoc retrieval result, document va workspace hien tai.
- Chi luu citation duoc generator su dung.
- Citation gom document ID, title, page, quote va score.

### BE-CHAT-006: Backend chua ho tro nhieu phien chat trong mot workspace

**Hien trang**

- Khong co `GET /api/chat/sessions/workspace/{workspaceId}` de liet ke cac session.
- Khong co API xoa mot session hoac xoa lich su theo workspace.
- `POST /api/chat/sessions` goi `createOrGetSession()` va luon tra active session hien co cua user/workspace.
- Bam New chat tren FE khong tao duoc Backend session moi; history Backend cua cac cuoc chat van bi tron trong mot session.

**Cach sua**

- Tach `createSession` khoi `getActiveSession`; create phai luon tao session moi.
- Them API list session theo authenticated user va workspace, sap xep `updatedAt DESC`.
- Them API deactivate/delete mot session va clear history workspace co kiem tra owner.
- Moi request ask phai chi lay context hoi thoai cua session hien tai.
- Trong khi cho BE sua, FE chi co the luu snapshot conversation trong localStorage; day khong phai session isolation that.

**Test cases**

- Tao ba cuoc chat phai sinh ba session ID khac nhau.
- Reload van liet ke du ba session va mo dung message cua tung session.
- Xoa mot session khong anh huong hai session con lai.
- Follow-up cua session A khong su dung message cua session B.

## 6. P1 - Embedding va Retrieval

### BE-RAG-001: Bon embedding model hien chi la metadata

**Hien trang**

Java tao hashed vector du model duoc dat ten BGE-M3, E5, PhoBERT hoac OpenAI.

**Cach sua**

Python cung cap:

- `POST /internal/embeddings/batch`
- `POST /internal/embeddings/query`
- `GET /internal/models/capabilities`

Java luu vector that theo `chunkId + embeddingModelId + modelVersion`.

### BE-RAG-002: Retrieval khong su dung embedding da luu

**Cach sua**

- Tao query embedding bang dung model da index document.
- Tinh cosine tren vector that.
- Ket hop semantic va lexical score theo config.
- Khong query model A tren chunk vector cua model B.
- Ghi model, threshold, topK va latency vao retrieval query.

### BE-RAG-003: Chua ho tro day du model nghien cuu

**Model muc tieu**

- `BAAI/bge-m3`
- `intfloat/multilingual-e5-base`
- `vinai/phobert-base` voi word segmentation va mean pooling chuan hoa
- `text-embedding-3-small` khi co OpenAI key

**Quy tac**

Thieu API key phai tra `UNAVAILABLE`; khong sinh so lieu gia.

### BE-RAG-004: Chua co benchmark chunking strategy

**Ba strategy bat buoc**

- Fixed: 500 tu, overlap 50.
- Paragraph: 700 tu, overlap 120.
- Heading/page-aware: 900 tu, overlap 120.
- PPTX giu ranh gioi slide; citation page tuong ung slide number.

Benchmark chunking dung index rieng theo experiment, khong ghi de index production.

## 7. P1 - Tach database

### BE-DATA-001: SQL Server va Python SQLite giu hai tap tai lieu

**Cach sua**

- SQL Server giu document, page, chunk, vector, session, test set va experiment.
- Python `/api/generate` chi nhan context tu Java.
- Benchmark nhan cases, config va corpus tu Java; khong tu doc SQLite.
- Dashboard doc ket qua tu SQL Server.
- Endpoint Python document/chat SQLite duoc danh dau legacy hoac loai khoi production.

**Test cases**

- Document ID trong citation phai la ID SQL Server.
- Xoa tai lieu o Java thi chat va benchmark khong con su dung tai lieu do.
- Restart Python khong lam mat du lieu he thong.

## 8. P1 - Test set va Evaluation

### BE-EVAL-001: Test set khong can khop MLN121 nhung phai co corpus tuong ung

**Quy tac**

- Co the giu 50 cau Software Testing hien tai.
- Phai upload tai lieu Software Testing lam nguon benchmark.
- Dataset luu `courseId`, `workspaceId` va `documentIds`.
- Moi cau co ground truth, expected source/page, category va out-of-scope flag.
- Khong benchmark cau Software Testing bang tai lieu Triet hoc.

### BE-EVAL-002: Dataset SQL Server chua du 50 cau

**Cach sua**

- Them bulk import CSV.
- Validate header, UTF-8, duplicate question va source/page.
- Import atomic: loi mot dong thi khong tao dataset nua voi.
- Luu checksum va version dataset.

### BE-EVAL-003: Experiment bo qua cau hinh embedding/chunking

`POST /api/evaluation/experiments` phai nhan:

- `datasetId`
- `experimentType`
- `embeddingModelId`
- `chunkingStrategy`
- `generationMode`
- `topK`
- `temperature`
- `seed`
- `configJson`

Java phai luu va truyen dung toan bo cau hinh sang Python.

### BE-EVAL-004: Benchmark truyen duong dan file local

**Cach sua**

- Khong gui duong dan nhu `C:\...\test.csv` sang Python.
- Java gui test cases payload hoac object-storage URL.
- Benchmark chay async, tra job ID va progress.
- Co idempotency key de tranh tao duplicate run khi retry.

## 9. P1 - Fine-tuning va RAGAS

### BE-FT-001: Fine-tuned model chua san sang

**Cach sua**

- Tao dataset train/validation rieng, khong dung lai 50 test questions.
- Chuan bi 150-300 QA train co human review.
- Train `Qwen2.5-0.5B-Instruct` bang LoRA/QLoRA.
- Luu adapter version, config, seed, dataset checksum va metrics.
- `finetuning/status` chi bao ready khi adapter load thanh cong.

### BE-FT-002: Fine-tuned-only chua tai lap duoc

Chay cung 50 test questions cho:

- RAG extractive.
- RAG base model.
- RAG + LoRA.
- Fine-tuned-only.

Khong cho fine-tuned model nhin thay test answers trong training data.

### BE-RAGAS-001: `/ai/evaluate` la placeholder

**Cach sua**

- Xoa ket qua danh gia gia lap.
- Tich hop thu vien RAGAS chinh thuc.
- Dung Qwen2.5-1.5B-Instruct local lam judge.
- Dung BGE-M3 lam evaluator embedding.
- Luu faithfulness, answer relevancy, context precision va context recall.
- Luu judge model, prompt version va evaluator version.

### BE-RAGAS-002: Proxy metric de bi hieu la RAGAS

**Cach sua**

- Doi ten ro `token_overlap_proxy`.
- Khong hien thi proxy trong cot RAGAS.
- Bao cao phan biet metric chinh thuc va metric noi bo.

## 10. P2 - Dashboard va bao cao

### BE-DASH-001: Dashboard API tra rong du co CSV report

**Cach sua**

- Persist moi run vao SQL Server.
- Aggregate theo model, chunking, generation mode va dataset version.
- Them `GET /api/evaluation/dashboard`.
- Them export CSV/JSON tu cung du lieu database.
- Khong doc truc tiep cac CSV run roi de dung dashboard.

**Tieu chi**

So experiment, completed run va metric tren dashboard phai khop per-question results.

## 11. P2 - Van hanh va chat luong code

### BE-OPS-001: Thieu migration co version

- Them Flyway.
- Baseline schema hien tai.
- Migration cho jobs, model versions, experiment config va aggregate metrics.
- Khong phu thuoc `ddl-auto=update` khi nghiem thu.

### BE-OPS-002: Thieu automated tests

- Java: unit service, repository integration va controller contract tests.
- Python: pytest cho chunker, embedding, generation, refusal, benchmark va RAGAS.
- Contract test Java-Python cho request/response schemas.
- CI chay build va tests cho ca hai Backend.

### BE-OPS-003: Error response khong thong nhat

Chuan hoa:

```json
{
  "success": false,
  "code": "CHAT_HISTORY_QUERY_FAILED",
  "message": "Could not load chat history.",
  "details": null,
  "requestId": "..."
}
```

Khong tra stack trace hoac internal SQL cho Frontend.

### BE-OPS-004: Thieu health va readiness

- Java health kiem tra SQL Server, Cloudinary va Python.
- Python health kiem tra embedding model, adapter va evaluator.
- Phan biet `/health/live` va `/health/ready`.

### BE-OPS-005: Secret da duoc chia se trong qua trinh phat trien

- Rotate Cloudinary secret.
- Chi luu secret trong `.env` hoac secret manager.
- Them `.env.example` khong chua gia tri that.
- Kiem tra Git history de bao dam khong co secret.

## 12. Thu tu trien khai

1. Khoa privilege escalation va thay JWT.
2. Sua history HTTP 500.
3. Sua quyen tai lieu workspace cho Admin/User.
4. Hoan thien upload/index job va trang thai.
5. Thay hash/keyword bang embedding that.
6. Hoan thien refusal, conversation context va citation.
7. Loai SQLite khoi production data flow.
8. Import test set 50 cau voi corpus tuong ung.
9. Hoan thien experiment parameters va async benchmark.
10. Train LoRA va tich hop RAGAS local.
11. Persist dashboard/report trong SQL Server.
12. Them migration, test automation, CI va README.

## 13. Test suite nghiem thu

### Authentication

- User khong the tu dang ky Admin.
- JWT gia/het han tra `401`.
- User goi role API tra `403`.

### Document

- Admin va User upload thanh cong PDF, DOCX va PPTX.
- User khac thay tai lieu dung chung trong workspace.
- Chi owner va Admin duoc sua/xoa/re-index.
- File sai MIME, qua dung luong hoac khong co text tra loi dung.

### Chat

- In-scope answer co citation dung file/page.
- Out-of-scope phai tu choi.
- Khong citation cheo workspace.
- Follow-up dung dung history.
- History hoat dong sau reload/login.

### Research

- Dataset du dung 50 cau va gan corpus phu hop.
- Chay ba chunking strategies.
- Chay BGE-M3, E5, PhoBERT va OpenAI khi available.
- Chay RAG, RAG-LoRA va fine-tuned-only.
- RAGAS co du bon metric chinh thuc.
- Dashboard va export co so lieu giong database.

### Reliability

- Restart Python trong luc Java dang chay phai tra loi co the retry.
- Benchmark retry khong tao duplicate run.
- Cloudinary loi khong de lai document gia `INDEXED`.
- Khong con HTTP 500, du lieu mock hoac result gia trong flow nghiem thu.

## 14. Dieu kien du an duoc xem la hoan thanh

- Hai Backend chay on dinh va co readiness check.
- SQL Server la nguon du lieu duy nhat cua production flow.
- Admin va User deu upload duoc; tai lieu dung chung trong workspace.
- Chat co history, conversation context, refusal va citation dung.
- Test set co 50 cau va corpus tuong ung, khong bat buoc MLN121.
- Co benchmark nhieu chunking strategy va nhieu embedding model.
- Fine-tuned model load va benchmark duoc.
- Co bang RAGAS chinh thuc va Research Dashboard du lieu that.
- README mo ta cach tai lap toan bo ket qua.
- CI chay thanh cong Java tests, Python tests va contract tests.

## 15. Retest AI Chat gan nhat - 2026-07-02

### Bang chung bo sung cho BE-CHAT-003

**Hien trang khi test**

- Workspace `Thong tin EXE201` co 3 document `Processed`: `Hi_User_Manual`, `NOTICE 2`, `NOTICE 1 (1)`.
- API chunks xac nhan SQL Server co du lieu:
  - `NOTICE 1 (1)` co 2 chunks.
  - `NOTICE 2` co 6 chunks.
  - `Hi_User_Manual` co 8 chunks.
- UI AI Chat gui cau hoi thanh cong, khong con loi HTTP 500.

**Ket qua test**

- Cau ngoai pham vi `Ai vo dich World Cup 2022?` duoc tu choi dung va khong co citation.
- Cau co lien quan tu nhien `NOTICE 1 noi ve noi dung gi?` van bi tu choi: `Xin loi, minh khong tim thay noi dung lien quan trong tai lieu cua workspace nay.`
- Cau bam sat chunk `MVP scope includes what features in Hi User Manual?` van bi tu choi.
- Cac cau tong quan `tai lieu nay co may chuong`, `thong tin file huong dan`, `noi dung file` cung bi tu choi sai.
- Direct retrieval voi cung workspace va cau `MVP scope includes what features in Hi User Manual?` tra ve 5 chunks, nhung top score chi `0.20`.
- `ChatService` dang chan answer neu `topScore < 0.25`, nen cau hoi co evidence nhung score keyword thap van bi coi la out-of-scope.
- Cau keyword cuc khop `Functional Features Completeness User Flows Different Roles Technical Quality System Stability UX UI Design MVP Deployment Accessibility` co top score `0.404` va flow tra loi + citation hoat dong.

**Nguyen nhan kha nang cao**

- `RetrievalService` dang dung `embeddingService.cosineKeywordScore(...)`, khong dung semantic embedding that.
- `ChatService` co gate cung `topScore < 0.25`.
- Text tieng Viet trong chunk/API co dau hieu mojibake, lam keyword matching voi cau hoi tieng Viet kem on dinh.

**Huong fix BE de nghiem thu**

- Dung semantic embedding that cho query va chunk theo dung active model.
- Calibrate threshold rieng cho in-scope/out-of-scope; khong hard-code `0.25` khi retrieval con la keyword cosine.
- Xu ly encoding UTF-8 dung tu luc extract, luu DB va tra API.
- Luu va tra citations trong history response hoac duy tri endpoint citation theo assistant message.
- Bo sung test: cau tu nhien co trong document phai tra answer + citation; cau ngoai pham vi phai tu choi.

## 16. Mau theo doi tien do

| Ma loi | Muc do | Trang thai | Owner | Pull request | Ket qua retest | Ghi chu |
|---|---|---|---|---|---|---|
| BE-AUTH-002 | P0 | OPEN | | | | |
| BE-AUTH-003 | P0 | OPEN | | | | |
| BE-DOC-002 | P0 | OPEN | | | | |
| BE-DOC-003 | P0 | OPEN | | | | |
| BE-DOC-004 | P0 | OPEN | | | | |
| BE-DOC-005 | P0 | OPEN | | | | |
| BE-CHAT-002 | P0 | OPEN | | | | |
| BE-CHAT-003 | P0 | OPEN | | | | |
| BE-CHAT-004 | P0 | OPEN | | | | |
| BE-CHAT-005 | P0 | OPEN | | | | |
| BE-CHAT-006 | P0 | OPEN | | | | FE dang luu snapshot local, BE van dung chung mot active session |
| BE-RAG-001 | P1 | OPEN | | | | |
| BE-RAG-002 | P1 | OPEN | | | | |
| BE-RAG-003 | P1 | OPEN | | | | |
| BE-RAG-004 | P1 | OPEN | | | | |
| BE-DATA-001 | P1 | OPEN | | | | |
| BE-EVAL-001 | P1 | OPEN | | | | |
| BE-EVAL-002 | P1 | OPEN | | | | |
| BE-EVAL-003 | P1 | OPEN | | | | |
| BE-EVAL-004 | P1 | OPEN | | | | |
| BE-FT-001 | P1 | OPEN | | | | |
| BE-FT-002 | P1 | OPEN | | | | |
| BE-RAGAS-001 | P1 | OPEN | | | | |
| BE-RAGAS-002 | P1 | OPEN | | | | |
| BE-DASH-001 | P2 | OPEN | | | | |
| BE-OPS-001 | P2 | OPEN | | | | |
| BE-OPS-002 | P2 | OPEN | | | | |
| BE-OPS-003 | P2 | OPEN | | | | |
| BE-OPS-004 | P2 | OPEN | | | | |
| BE-OPS-005 | P2 | OPEN | | | | |

## 17. Retest RAG va Fine-tuning - 2026-07-28

### RAG student chat

- Python `/api/generate` da co buoc kiem tra answer co duoc context ho tro hay khong.
- Answer sai ngon ngu, bi cat giua cau hoac khong du evidence se chuyen sang grounded extractive fallback.
- Fallback chi tra `used_chunk_ids` thuc su da dung; citation khong con mac dinh gan tat ca top chunks.
- Smoke test 5 cau dau cua test set Triet hoc tra `5/5` response hop le va co dung chunk ID.
- Cau hoi `Xu huong noi bat cua triet hoc An Do co dai la gi?` da duoc fallback thanh evidence day du thay vi output Qwen bi cat giua tu.
- Python test suite: `39 passed`.
- Van can retest qua Java voi tai lieu da re-index BGE-M3 trong SQL Server truoc khi dong `BE-CHAT-003`.

### Fine-tuned-only

- Adapter chinh thuc ton tai tai `models/qwen2.5-0.5b-triethoc-lora-v1`.
- Manifest ghi dung base model `Qwen/Qwen2.5-0.5B-Instruct`, dataset `triethoc-v1`, checksum PDF va dataset.
- Dataset co 250 train, 50 validation, 50 locked test; semantic leakage scan bang BGE-M3 co `0` warning.
- Training CUDA hoan tat voi `train_loss=1.8514`, `eval_loss=1.5642`, peak VRAM khoang `2.75 GB`.
- Behavioral gate khong dat:
  - answer token F1: `0.2235`, yeu cau toi thieu `0.35`;
  - refusal accuracy: `1.0`, yeu cau toi thieu `0.80`.
- `/api/model/status` tra `BASE_RAG_READY` va `QUALITY_GATE_FAILED`.
- Khong bat `FINETUNING_ALLOW_UNVERIFIED`; strict benchmark tiep tuc bi chan de khong tao so lieu nghien cuu sai.
- Java readiness da tra them `fineTunedStatus`; blocker hien ro
  `Strict FINE_TUNED model is not ready: QUALITY_GATE_FAILED.`
- `BE-FT-001` va `BE-FT-002` van `OPEN`. Huong tiep theo la tang nang luc base model
  hoac bo sung QA human-reviewed, sau do train va dat lai cung quality gate; khong ha nguong de hop thuc hoa adapter.

### Build va regression

- Java Maven tests: `60 passed`.
- FE tests: `26 passed`.
- FE ESLint: `0` error, `3` canh bao Fast Refresh cu.
- FE Vite production build: thanh cong.
