# Lenh chay project SWP

File nay gom cac lenh copy nhanh de team chay project. Tat ca lenh ben duoi gia su terminal dang mo o root repo, vi du thu muc chua `BACKEND` va `FRONTEND`.

Khong dung duong dan co dinh nhu `C:\DEV\SWP` de moi may deu chay duoc.


## 1. Chay day du project

Can mo 3 terminal rieng:

- Terminal 1: Python AI service.
- Terminal 2: Java Spring backend.
- Terminal 3: Frontend Vite.


## 2. Terminal 1 - Chay Python AI Fine-tuning

Dung khi test Workflow 5 hoac Fine-tuning benchmark.

```cmd
cd BACKEND
.venv\Scripts\activate.bat
set GENERATION_PROVIDER=lora
set LORA_ADAPTER_DIR=models\qwen-rag-lora
set LOCAL_MAX_NEW_TOKENS=25
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

Ghi chu:
- `LOCAL_MAX_NEW_TOKENS=25` giup demo nhanh.
- Neu muon cau tra loi dai hon co the tang len `40` hoac `50`, nhung benchmark se cham hon.
- Lan dau chay co the mat thoi gian tai/cache base model Qwen tu Hugging Face.
- Neu PowerShell khong chay duoc activate, hay mo terminal CMD.


## 3. Terminal 1 - Chay Python AI RAG/mac dinh

Neu chi test RAG, co the chay gon hon:

```cmd
cd BACKEND
.venv\Scripts\activate.bat
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

Kiem tra Python AI:

```cmd
curl http://127.0.0.1:8001/api/health
```

Hoac mo:

```txt
http://127.0.0.1:8001
```


## 4. Terminal 2 - Chay Java Spring backend

```cmd
cd BACKEND\courseqa-springboot-mvc2-skeleton\BACKEND
mvn spring-boot:run
```

Neu may co Maven wrapper thi co the dung:

```cmd
cd BACKEND\courseqa-springboot-mvc2-skeleton\BACKEND
mvnw.cmd spring-boot:run
```

Kiem tra API Spring neu co endpoint health/swagger:

```txt
http://localhost:8080
```


## 5. Terminal 3 - Chay Frontend

Lan dau pull ve thi cai package:

```cmd
cd FRONTEND
npm install
```

Chay FE:

```cmd
cd FRONTEND
npm run dev
```

Mo tren trinh duyet:

```txt
http://localhost:5173
```


## 6. Thu tu chay khuyen dung

```txt
1. Chay Python AI service truoc.
2. Chay Java Spring backend.
3. Chay Frontend.
4. Vao FE test workflow.
```

Ly do: Java Spring can goi Python AI khi chay benchmark/RAG/Fine-tuning.


## 7. Kiem tra GPU PyTorch

Chay trong terminal da activate `.venv` va dang o `BACKEND`:

```cmd
python -c "import torch; print(torch.__version__); print(torch.version.cuda); print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'NO CUDA')"
```

Neu dung GPU CUDA thi ky vong co:

```txt
True
NVIDIA GeForce ...
```

Neu ra `False` thi may dang dung Torch CPU, Fine-tuning benchmark va train se cham hon rat nhieu.


## 8. Train lai model Fine-tuning bang CSV bat ky

CSV can co toi thieu 2 cot:

```txt
question,expected_answer
```

Chay train tu root repo:

```cmd
cd BACKEND
.venv\Scripts\activate.bat
train_active.bat path\to\file.csv --run-name my-new-model
```

Vi du neu file CSV nam trong `BACKEND\data`:

```cmd
cd BACKEND
.venv\Scripts\activate.bat
train_active.bat data\ground_truth_triethocmaclenin_10_demo.csv --run-name demo-10
```

Sau khi train xong, chay Python AI bang adapter moi:

```cmd
set GENERATION_PROVIDER=lora
set LORA_ADAPTER_DIR=models\my-new-model
set LOCAL_MAX_NEW_TOKENS=25
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```


## 9. Dry-run truoc khi train

Dung de kiem tra file train/validation va package:

```cmd
cd BACKEND
.venv\Scripts\activate.bat
python experiments\train_lora.py --dry-run
```

Neu bao thieu package thi cai them trong `.venv`.


## 10. Lenh train thu cong bang config co san

Neu da co:

```txt
BACKEND\data\finetuning\train.jsonl
BACKEND\data\finetuning\validation.jsonl
BACKEND\experiments\lora_config.json
```

Chay:

```cmd
cd BACKEND
.venv\Scripts\activate.bat
python experiments\train_lora.py --config experiments\lora_config.json
```


## 11. Giam thoi gian benchmark Fine-tuning

Neu 10 cau mat qua lau, giam token:

```cmd
set LOCAL_MAX_NEW_TOKENS=20
```

Hoac:

```cmd
set LOCAL_MAX_NEW_TOKENS=25
```

Sau khi doi bien env, can tat va chay lai Python AI service.

Khuyen dung demo:

```txt
Dataset 3-5 cau: nhanh nhat.
Dataset 10 cau: demo on neu may co GPU.
Dataset 100 cau: dung test day du, khong nen demo truc tiep.
```


## 12. Cac file khong nen push

Khong push:

```txt
.env
.venv
data/models_cache
data/db
*.log
target/
node_modules/
```

Can than voi:

```txt
BACKEND/courseqa-springboot-mvc2-skeleton/BACKEND/src/main/resources/application.properties
```

Neu trong file nay co password DB local thi khong nen commit/push.
