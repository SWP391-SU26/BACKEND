# Fine-tuning Training

File này hướng dẫn team chạy Fine-tuning bằng một file CSV được chỉ định. Không hard-code môn học hay đường dẫn máy cá nhân.

## 1. Pull về test model đã train sẵn

Repo đã có sẵn demo LoRA adapter:

```text
models/qwen-rag-lora
```

Nếu chỉ cần chạy demo/test Fine-tuning thì không cần train lại. Chạy Python AI service như sau:

```cmd
cd C:\DEV\SWP\BACKEND
.venv\Scripts\activate.bat
set GENERATION_PROVIDER=lora
set LORA_ADAPTER_DIR=models\qwen-rag-lora
set LOCAL_MAX_NEW_TOKENS=80
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

Check model:

```cmd
curl http://127.0.0.1:8001/api/model/status
```

Kết quả đúng cần có:

```text
"configured_provider":"lora"
"adapter_exists":true
"local_model_loaded":true
```

## 2. Setup trước khi train

Chạy trong thư mục `BACKEND`:

```cmd
cd C:\DEV\SWP\BACKEND
python -m venv .venv
.venv\Scripts\activate.bat
python -m pip install --upgrade pip
pip install -r requirements.txt
pip install datasets trl transformers peft accelerate bitsandbytes
```

Nếu train QLoRA bằng NVIDIA GPU, cài PyTorch CUDA:

```cmd
pip uninstall -y torch torchvision torchaudio
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu128
```

Kiểm tra CUDA:

```cmd
python -c "import torch; print(torch.__version__); print(torch.version.cuda); print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'NO CUDA')"
```

Nếu dòng thứ ba là `False` hoặc in ra `NO CUDA`, QLoRA sẽ không train được bằng GPU.

## 3. Chuẩn CSV đầu vào

CSV tối thiểu cần có 2 cột:

```csv
question,expected_answer
```

CSV benchmark đầy đủ cũng dùng được:

```csv
question,expected_answer,expected_source,expected_page,subject,is_out_of_scope,category
```

Các dòng thiếu `question`, thiếu `expected_answer`, hoặc `is_out_of_scope=true` sẽ bị bỏ qua khi tạo dữ liệu train.

## 4. Dry-run trước khi train

Luôn chạy dry-run trước để kiểm tra CSV, JSONL output và package còn thiếu:

```cmd
train_active.bat data\ground_truth_triethocmaclenin_10_demo.csv --run-name demo-test --dry-run
```

Nếu đường dẫn CSV có dấu cách, bọc bằng dấu nháy kép:

```cmd
train_active.bat "C:\Users\A\Downloads\my data.csv" --run-name demo-test --dry-run
```

Dry-run OK khi thấy:

```json
"missing_packages": []
```

và:

```json
"valid": true
```

cho cả train dataset và validation dataset.

## 5. Train bằng CSV được chỉ định

Cú pháp:

```cmd
train_active.bat path\to\file.csv --run-name ten-run
```

Ví dụ:

```cmd
train_active.bat data\ground_truth_triethocmaclenin_10_demo.csv --run-name demo-test
train_active.bat C:\Users\A\Downloads\ground_truth.csv --run-name final-demo
train_active.bat D:\datasets\qa.csv --run-name dataset-v2
```

`--run-name` dùng để đặt tên output. Mỗi run sẽ có thư mục riêng, không ghi đè adapter cũ nếu đặt tên khác nhau.

## 6. Output sau khi train

Với:

```cmd
train_active.bat data\my_data.csv --run-name final-demo
```

Script sẽ tạo:

```text
data/finetuning/final-demo/train.jsonl
data/finetuning/final-demo/validation.jsonl
data/finetuning/final-demo/dataset_summary.json
experiments/generated/lora_config_final-demo.json
models/final-demo/
```

Muốn chạy adapter mới:

```cmd
set GENERATION_PROVIDER=lora
set LORA_ADAPTER_DIR=models\final-demo
set LOCAL_MAX_NEW_TOKENS=80
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

## 7. Lưu ý khi share cho team

Adapter demo `models/qwen-rag-lora/` đã được commit sẵn để team pull về test.

Các adapter mới tạo dưới `models/<run-name>/` vẫn bị ignore mặc định. Nếu muốn share adapter mới, dùng một trong các cách:

- Git LFS cho `models/<run-name>/`
- GitHub Release artifact
- Google Drive/OneDrive
- Hugging Face model repo

Không commit `.venv`, cache model, hoặc database local.

## 8. Lỗi thường gặp

Thiếu package:

```text
"missing_packages": ["datasets", "trl", "bitsandbytes"]
```

Cài lại:

```cmd
pip install datasets trl transformers peft accelerate bitsandbytes
```

Không có CUDA:

```text
RuntimeError: QLoRA cần CUDA GPU
```

Cài lại PyTorch CUDA hoặc đổi `use_qlora=false` trong config nếu chỉ muốn thử CPU/LoRA, nhưng CPU sẽ rất chậm.

Python trong `.venv` không chạy:

```cmd
where python
.venv\Scripts\python.exe --version
```

Nếu lỗi, xóa và tạo lại venv:

```cmd
rmdir /s /q .venv
python -m venv .venv
.venv\Scripts\activate.bat
pip install -r requirements.txt
pip install datasets trl transformers peft accelerate bitsandbytes
```

Benchmark Fine-tuning lâu:

- 100 câu có thể hơn 20 phút trên RTX 3050 Ti.
- Demo nên dùng file 10 câu: `data\ground_truth_triethocmaclenin_10_demo.csv`.
- Có thể giảm output bằng:

```cmd
set LOCAL_MAX_NEW_TOKENS=80
```
