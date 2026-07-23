from __future__ import annotations

import argparse
from datetime import datetime, timezone
import importlib.util
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from src.finetuning import training_source_names, validate_jsonl
from src.embeddings import find_cached_snapshot


REQUIRED_PACKAGES = ["torch", "transformers", "datasets", "peft", "trl"]


def required_packages_for(config: dict) -> list[str]:
    packages = list(REQUIRED_PACKAGES)
    if bool(config.get("use_qlora", True)):
        packages.append("bitsandbytes")
    return packages


def main() -> None:
    parser = argparse.ArgumentParser(description="Fine-tune baseline bằng LoRA/QLoRA.")
    parser.add_argument("--config", default=str(ROOT / "experiments" / "lora_config.json"))
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    config = json.loads(Path(args.config).read_text(encoding="utf-8"))
    train_validation = validate_jsonl(resolve_path(config["train_file"]))
    validation_validation = validate_jsonl(resolve_path(config["validation_file"]))
    missing = [name for name in required_packages_for(config) if importlib.util.find_spec(name) is None]
    summary = {
        "config": config,
        "train_dataset": train_validation,
        "validation_dataset": validation_validation,
        "missing_packages": missing,
    }
    if args.dry_run:
        print(json.dumps(summary, ensure_ascii=False, indent=2))
        return
    if missing:
        raise RuntimeError(
            "Thiếu package fine-tuning: "
            + ", ".join(missing)
            + ". Cài bằng requirements-finetuning.txt."
        )
    if not train_validation["valid"]:
        raise RuntimeError("Train dataset không hợp lệ.")

    run_training(config)


def run_training(config: dict) -> dict:
    import torch
    from datasets import concatenate_datasets, load_dataset
    from peft import LoraConfig, PeftModel, get_peft_model
    from transformers import (
        AutoModelForCausalLM,
        AutoTokenizer,
        BitsAndBytesConfig,
        DataCollatorForSeq2Seq,
        Trainer,
        TrainingArguments,
    )

    model_name = config["model_name"]
    model_cache_dir = resolve_path(config.get("model_cache_dir", "data/models_cache/hub"))
    cached_model = find_cached_snapshot(model_cache_dir, model_name, "model.safetensors")
    model_source = str(cached_model) if cached_model else model_name
    tokenizer_name = config.get("tokenizer_name", model_name)
    tokenizer_path = resolve_path(tokenizer_name)
    cached_tokenizer = find_cached_snapshot(model_cache_dir, tokenizer_name, "tokenizer.json")
    tokenizer_source = (
        str(tokenizer_path)
        if tokenizer_path.exists()
        else (str(cached_tokenizer) if cached_tokenizer else tokenizer_name)
    )
    use_qlora = bool(config.get("use_qlora", True))
    quantization_config = None
    if use_qlora:
        if not torch.cuda.is_available():
            raise RuntimeError("QLoRA cần CUDA GPU. Đặt use_qlora=false để thử LoRA.")
        quantization_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=torch.bfloat16,
            bnb_4bit_use_double_quant=True,
        )

    tokenizer = AutoTokenizer.from_pretrained(
        tokenizer_source,
        use_fast=True,
        local_files_only=Path(tokenizer_source).exists(),
    )
    if not tokenizer.chat_template and Path(tokenizer_source).exists():
        template_path = Path(tokenizer_source) / "chat_template.jinja"
        if template_path.exists():
            tokenizer.chat_template = template_path.read_text(encoding="utf-8")
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
    model_kwargs = {
        "quantization_config": quantization_config,
        "local_files_only": bool(cached_model),
    }
    if torch.cuda.is_available():
        model_kwargs["device_map"] = "auto"
    else:
        cpu_dtype = str(config.get("cpu_dtype", "float32")).lower()
        model_kwargs["dtype"] = torch.bfloat16 if cpu_dtype == "bfloat16" else torch.float32
    model = AutoModelForCausalLM.from_pretrained(model_source, **model_kwargs)
    dataset = load_dataset(
        "json",
        data_files={
            "train": str(resolve_path(config["train_file"])),
            "validation": str(resolve_path(config["validation_file"])),
        },
    )
    refusal_factor = max(1, int(config.get("refusal_oversample_factor", 1)))
    if refusal_factor > 1:
        refusals = dataset["train"].filter(
            lambda example: bool((example.get("metadata") or {}).get("is_out_of_scope")),
            load_from_cache_file=False,
        )
        if len(refusals):
            dataset["train"] = concatenate_datasets(
                [dataset["train"]] + [refusals] * (refusal_factor - 1)
            ).shuffle(seed=int(config.get("seed", 42)))
    max_length = int(config.get("max_length", 1024))

    def tokenize_example(example: dict) -> dict:
        messages = example["messages"]
        assistant_index = next(
            (index for index in range(len(messages) - 1, -1, -1) if messages[index]["role"] == "assistant"),
            -1,
        )
        if assistant_index < 0:
            raise ValueError("Training example does not contain an assistant answer.")
        full_text = tokenizer.apply_chat_template(
            messages, tokenize=False, add_generation_prompt=False
        )
        prompt_text = tokenizer.apply_chat_template(
            messages[:assistant_index], tokenize=False, add_generation_prompt=True
        )
        full_ids = tokenizer(
            full_text, add_special_tokens=False, truncation=True, max_length=max_length
        )["input_ids"]
        prompt_ids = tokenizer(
            prompt_text, add_special_tokens=False, truncation=True, max_length=max_length
        )["input_ids"]
        prompt_length = min(len(prompt_ids), len(full_ids))
        labels = [-100] * prompt_length + list(full_ids[prompt_length:])
        return {
            "input_ids": list(full_ids),
            "attention_mask": [1] * len(full_ids),
            "labels": labels,
        }

    dataset = dataset.map(
        tokenize_example,
        remove_columns=dataset["train"].column_names,
        desc="Tokenizing supervised fine-tuning data",
        load_from_cache_file=False,
    )
    token_counts = [len(row) for row in dataset["train"]["input_ids"]]
    supervised_counts = [sum(label != -100 for label in row) for row in dataset["train"]["labels"]]
    if (
        not token_counts
        or min(token_counts) < 20
        or sum(token_counts) < len(token_counts) * 50
        or min(supervised_counts) < 4
    ):
        raise RuntimeError("Tokenized training data is unexpectedly empty or too short.")

    initial_adapter = config.get("init_adapter_dir")
    if initial_adapter:
        initial_adapter_path = resolve_path(initial_adapter)
        initial_manifest_path = initial_adapter_path / "training_manifest.json"
        if not initial_manifest_path.exists():
            raise RuntimeError("The initial adapter is missing training_manifest.json.")
        initial_manifest = json.loads(initial_manifest_path.read_text(encoding="utf-8"))
        if initial_manifest.get("base_model") != model_name:
            raise RuntimeError("The initial adapter was built for a different base model.")
        model = PeftModel.from_pretrained(model, initial_adapter_path, is_trainable=True)
    else:
        lora_config = LoraConfig(
            r=int(config.get("lora_r", 16)),
            lora_alpha=int(config.get("lora_alpha", 32)),
            lora_dropout=float(config.get("lora_dropout", 0.05)),
            bias="none",
            task_type="CAUSAL_LM",
            target_modules=config.get("target_modules"),
        )
        model = get_peft_model(model, lora_config)
    training_config = TrainingArguments(
        output_dir=str(resolve_path(config["output_dir"])),
        num_train_epochs=float(config.get("epochs", 3)),
        max_steps=int(config.get("max_steps", -1)),
        per_device_train_batch_size=int(config.get("batch_size", 1)),
        gradient_accumulation_steps=int(config.get("gradient_accumulation_steps", 8)),
        learning_rate=float(config.get("learning_rate", 2e-4)),
        logging_steps=1,
        save_strategy="epoch",
        eval_strategy="epoch" if len(dataset["validation"]) else "no",
        report_to="none",
        use_cpu=not torch.cuda.is_available(),
        bf16=bool(torch.cuda.is_available() and torch.cuda.is_bf16_supported()),
        fp16=False,
        optim="adamw_torch",
        dataloader_pin_memory=torch.cuda.is_available(),
    )
    trainer = Trainer(
        model=model,
        args=training_config,
        train_dataset=dataset["train"],
        eval_dataset=dataset["validation"] if len(dataset["validation"]) else None,
        data_collator=DataCollatorForSeq2Seq(
            tokenizer=tokenizer,
            model=model,
            padding=True,
            label_pad_token_id=-100,
        ),
        processing_class=tokenizer,
    )
    train_result = trainer.train()
    evaluation_metrics = trainer.evaluate() if len(dataset["validation"]) else {}
    evaluation_metrics["eval_num_tokens"] = sum(
        sum(label != -100 for label in labels) for labels in dataset["validation"]["labels"]
    )
    evaluation_metrics.update({
        key: value for key, value in train_result.metrics.items() if key not in evaluation_metrics
    })
    trainer.save_model(str(resolve_path(config["output_dir"])))
    tokenizer.save_pretrained(str(resolve_path(config["output_dir"])))
    manifest = build_training_manifest(config, evaluation_metrics)
    output_dir = resolve_path(config["output_dir"])
    (output_dir / "training_manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return manifest


def build_training_manifest(config: dict, evaluation_metrics: dict) -> dict:
    train_path = resolve_path(config["train_file"])
    validation_path = resolve_path(config["validation_file"])
    train_rows = read_jsonl(train_path)
    validation_rows = read_jsonl(validation_path)
    rows = train_rows + validation_rows
    refusal_examples = sum(bool((row.get("metadata") or {}).get("is_out_of_scope")) for row in rows)
    minimum_examples = int(config.get("min_training_examples", 100))
    minimum_refusals = int(config.get("min_refusal_examples", max(10, round(len(rows) * 0.1))))
    maximum_eval_loss = float(config.get("max_eval_loss", 3.0))
    raw_eval_loss = evaluation_metrics.get("eval_loss")
    eval_loss = float(raw_eval_loss) if raw_eval_loss is not None else None
    checks = {
        "enough_examples": len(rows) >= minimum_examples,
        "enough_refusal_examples": refusal_examples >= minimum_refusals,
        "has_validation": bool(validation_rows),
        "acceptable_eval_loss": (
            eval_loss is not None and math.isfinite(eval_loss) and 1e-6 < eval_loss <= maximum_eval_loss
        ),
        "enough_evaluated_tokens": float(evaluation_metrics.get("eval_num_tokens") or 0)
        >= max(1, len(validation_rows) * 20),
        "nonzero_train_loss": float(evaluation_metrics.get("train_loss") or 0) > 1e-6,
        "behavioral_smoke_test": bool(evaluation_metrics.get("behavioral_smoke_test", False)),
    }
    return {
        "schema_version": 1,
        "created_at": datetime.now(timezone.utc).isoformat(),
        "base_model": config["model_name"],
        "sources": sorted(training_source_names([train_path, validation_path])),
        "train_examples": len(train_rows),
        "validation_examples": len(validation_rows),
        "refusal_examples": refusal_examples,
        "evaluation_metrics": {
            key: value for key, value in evaluation_metrics.items() if isinstance(value, (int, float, str, bool))
        },
        "quality_gate": {
            "passed": all(checks.values()),
            "checks": checks,
            "thresholds": {
                "min_training_examples": minimum_examples,
                "min_refusal_examples": minimum_refusals,
                "max_eval_loss": maximum_eval_loss,
            },
        },
        "config": config,
    }


def read_jsonl(path: Path) -> list[dict]:
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def resolve_path(value: str) -> Path:
    path = Path(value)
    return path.resolve() if path.is_absolute() else (ROOT / path).resolve()


if __name__ == "__main__":
    main()
