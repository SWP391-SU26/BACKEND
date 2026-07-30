---
base_model: Qwen/Qwen2.5-1.5B-Instruct
library_name: peft
pipeline_tag: text-generation
tags:
  - lora
  - transformers
  - vietnamese
  - research
---

# Qwen2.5 1.5B Triet Hoc LoRA

This directory contains the portable PEFT/LoRA adapter used by the SWP
research module.

## Runtime

- Base model: `Qwen/Qwen2.5-1.5B-Instruct`
- Adapter: `qwen2.5-1.5b-triethoc-lora-v1`
- Dataset: `triethoc-v1`
- Intended use: RAG versus fine-tuned research experiments

The base model is not committed to Git. Transformers downloads it from
Hugging Face on the first run and stores it under `data/models_cache`.

The adapter currently does not pass the behavioral quality gate recorded in
`training_manifest.json`. Student chat continues to use grounded RAG with the
base model. Loading this adapter is limited to explicitly acknowledged
research runs.
