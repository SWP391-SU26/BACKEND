# Fine-tuning Training

Use `train_active.bat` from the `BACKEND` folder to prepare JSONL data and train a LoRA/QLoRA adapter without hard-coded machine paths.

## Quick Start

```cmd
cd C:\path\to\SWP\BACKEND
train_active.bat ..\ground_truth_triethocmaclenin_100.csv --run-name triet301
```

For another subject, pass another CSV and run name:

```cmd
train_active.bat data\math_ground_truth.csv --run-name math101
```

The CSV must include at least:

```text
question,expected_answer
```

The full benchmark CSV format is also supported:

```text
question,expected_answer,expected_source,expected_page,subject,is_out_of_scope,category
```

## Outputs

For `--run-name triet301`, generated files are:

```text
data/finetuning/triet301/train.jsonl
data/finetuning/triet301/validation.jsonl
experiments/generated/lora_config_triet301.json
models/triet301/
```

This keeps different subjects from overwriting each other.

## Dry Run

Validate data and packages without training:

```cmd
train_active.bat ..\ground_truth_triethocmaclenin_100.csv --run-name triet301 --dry-run
```

## Sharing A Trained Adapter

The repository currently ignores `models/`, so trained adapters are not committed by default.

To share with teammates, use one of these:

- Git LFS for `models/<run-name>/`
- A release artifact
- Google Drive/OneDrive
- Hugging Face model repo

Each teammate still needs the base model used in `experiments/lora_config.json`, for example:

```text
Qwen/Qwen2.5-0.5B-Instruct
```

To run the trained adapter in the Python service:

```cmd
set GENERATION_PROVIDER=lora
set LORA_ADAPTER_DIR=models\triet301
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```
