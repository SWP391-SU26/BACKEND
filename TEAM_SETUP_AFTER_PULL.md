# Team Setup After Pull

File này dành cho thành viên pull branch `develop` về để chạy workflow demo RAG + Fine-tuning.

## 1. Pull code

```cmd
cd C:\DEV\SWP\BACKEND
git checkout develop
git pull origin develop
```

## 2. Tạo Python virtual environment

```cmd
cd C:\DEV\SWP\BACKEND
python -m venv .venv
.venv\Scripts\activate.bat
python -m pip install --upgrade pip
pip install -r requirements.txt
pip install datasets trl transformers peft accelerate bitsandbytes
```

Nếu máy có NVIDIA GPU, cài PyTorch CUDA:

```cmd
pip uninstall -y torch torchvision torchaudio
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu128
```

Kiểm tra GPU:

```cmd
python -c "import torch; print(torch.__version__); print(torch.version.cuda); print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'NO CUDA')"
```

## 3. Chạy Python AI service với LoRA đã train

Repo đã có sẵn adapter:

```text
models/qwen2.5-1.5b-triethoc-lora-v1
```

Chạy:

```cmd
cd C:\DEV\SWP\BACKEND
.venv\Scripts\activate.bat
set GENERATION_PROVIDER=lora
set LOCAL_BASE_MODEL=Qwen/Qwen2.5-1.5B-Instruct
set LORA_ADAPTER_DIR=models/qwen2.5-1.5b-triethoc-lora-v1
set FINETUNING_ALLOW_UNVERIFIED=true
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

Check:

```cmd
curl http://127.0.0.1:8001/api/model/status
```

Kết quả đúng cần có:

```text
"configured_provider":"lora"
"adapter_exists":true
"local_model_loaded":true
```

## 4. Chạy Spring backend

```cmd
cd C:\DEV\SWP\BACKEND\courseqa-springboot-mvc2-skeleton\BACKEND
mvn spring-boot:run
```

Lưu ý DB SQL Server là local. Nếu password mỗi máy khác nhau, chỉnh riêng `application.properties` local, đừng commit password cá nhân.

## 5. File CSV demo 10 câu

File demo nhanh đã có sẵn:

```text
data/ground_truth_triethocmaclenin_10_demo.csv
```

Trong frontend:

1. Vào `Test Set / Ground Truth`.
2. `Create dataset`.
3. `Import CSV`.
4. Chọn file `BACKEND\data\ground_truth_triethocmaclenin_10_demo.csv`.
5. Tạo `Fine-tuning record`.
6. `Run benchmark`.

10 câu thường mất vài phút. 100 câu có thể mất hơn 20 phút trên RTX 3050 Ti.

## 6. Khi muốn train môn khác

Chuẩn bị CSV có ít nhất:

```text
question,expected_answer
```

Rồi chạy:

```cmd
cd C:\DEV\SWP\BACKEND
train_active.bat data\ten_file_mon_hoc.csv --run-name ten-mon
```

Output riêng cho môn đó:

```text
data/finetuning/ten-mon/
models/ten-mon/
experiments/generated/lora_config_ten-mon.json
```

Muốn chạy adapter mới:

```cmd
set GENERATION_PROVIDER=lora
set LORA_ADAPTER_DIR=models/ten-mon
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

## 7. VNPay Sandbox

Xem [VNPAY_PRO_SETUP.md](VNPAY_PRO_SETUP.md). Local Sandbox dùng Return URL `http://localhost:8080` và không bắt buộc tunnel; mỗi thành viên vẫn phải tự tạo `.env` và điền credential Sandbox qua kênh bảo mật.
