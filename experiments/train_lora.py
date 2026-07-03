from __future__ import annotations

import argparse
import importlib.util
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from src.finetuning import validate_jsonl


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


def run_training(config: dict) -> None:
    import torch
    from datasets import load_dataset
    from peft import LoraConfig
    from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig
    from trl import SFTConfig, SFTTrainer

    model_name = config["model_name"]
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

    tokenizer = AutoTokenizer.from_pretrained(model_name, use_fast=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
    model = AutoModelForCausalLM.from_pretrained(
        model_name,
        device_map="auto",
        quantization_config=quantization_config,
    )
    dataset = load_dataset(
        "json",
        data_files={
            "train": str(resolve_path(config["train_file"])),
            "validation": str(resolve_path(config["validation_file"])),
        },
    )

    def format_example(example: dict) -> str:
        return tokenizer.apply_chat_template(
            example["messages"],
            tokenize=False,
            add_generation_prompt=False,
        )

    lora_config = LoraConfig(
        r=int(config.get("lora_r", 16)),
        lora_alpha=int(config.get("lora_alpha", 32)),
        lora_dropout=float(config.get("lora_dropout", 0.05)),
        bias="none",
        task_type="CAUSAL_LM",
        target_modules=config.get("target_modules"),
    )
    training_config = SFTConfig(
        output_dir=str(resolve_path(config["output_dir"])),
        num_train_epochs=float(config.get("epochs", 3)),
        per_device_train_batch_size=int(config.get("batch_size", 1)),
        gradient_accumulation_steps=int(config.get("gradient_accumulation_steps", 8)),
        learning_rate=float(config.get("learning_rate", 2e-4)),
        logging_steps=1,
        save_strategy="epoch",
        eval_strategy="epoch" if len(dataset["validation"]) else "no",
        max_length=int(config.get("max_length", 1024)),
        report_to="none",
    )
    trainer = SFTTrainer(
        model=model,
        args=training_config,
        train_dataset=dataset["train"],
        eval_dataset=dataset["validation"] if len(dataset["validation"]) else None,
        peft_config=lora_config,
        formatting_func=format_example,
    )
    trainer.train()
    trainer.save_model(str(resolve_path(config["output_dir"])))
    tokenizer.save_pretrained(str(resolve_path(config["output_dir"])))


def resolve_path(value: str) -> Path:
    path = Path(value)
    return path.resolve() if path.is_absolute() else (ROOT / path).resolve()


if __name__ == "__main__":
    main()
