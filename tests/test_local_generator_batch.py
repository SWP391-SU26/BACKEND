from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from threading import Lock
import time

from src.config import AppSettings
from src.local_generator import LocalLoraGenerator
from src.storage import RetrievedChunk


class FakeTokenizer:
    pad_token_id = 0
    eos_token_id = 1

    def apply_chat_template(self, messages, add_generation_prompt=True, tokenize=False):
        rendered = "|".join(message["content"] for message in messages)
        return list(range(len(rendered))) if tokenize else rendered


def generator_without_model() -> LocalLoraGenerator:
    generator = LocalLoraGenerator.__new__(LocalLoraGenerator)
    generator.tokenizer = FakeTokenizer()
    generator._inference_lock = Lock()
    return generator


def test_benchmark_defaults_are_bounded() -> None:
    settings = AppSettings()
    assert settings.benchmark_batch_size == 4
    assert settings.benchmark_max_input_tokens == 1536
    assert settings.benchmark_max_new_tokens == 192


def test_cuda_oom_splits_batch_and_preserves_order(monkeypatch) -> None:
    generator = generator_without_model()
    calls = []

    def fake_generate(prompts, **_kwargs):
        calls.append(list(prompts))
        if len(prompts) > 2:
            raise RuntimeError("CUDA out of memory")
        return [f"answer:{prompt}" for prompt in prompts]

    monkeypatch.setattr(generator, "_generate_prompt_batch", fake_generate)
    answers = generator._generate_prompts_adaptive(
        ["q1", "q2", "q3", "q4"], max_new_tokens=64, max_input_tokens=448
    )

    assert answers == ["answer:q1", "answer:q2", "answer:q3", "answer:q4"]
    assert [len(call) for call in calls] == [4, 2, 2]


def test_only_one_generation_enters_the_gpu_section(monkeypatch) -> None:
    generator = generator_without_model()
    active = 0
    maximum = 0
    guard = Lock()

    def fake_generate(prompts, **_kwargs):
        nonlocal active, maximum
        with guard:
            active += 1
            maximum = max(maximum, active)
        time.sleep(0.03)
        with guard:
            active -= 1
        return list(prompts)

    monkeypatch.setattr(generator, "_generate_prompts_adaptive", fake_generate)
    with ThreadPoolExecutor(max_workers=2) as pool:
        futures = [
            pool.submit(
                generator._generate_messages_batch,
                [[{"role": "user", "content": question}]],
                max_new_tokens=64,
                max_input_tokens=448,
            )
            for question in ("q1", "q2")
        ]
        [future.result() for future in futures]

    assert maximum == 1


def test_rag_context_selection_respects_input_budget() -> None:
    generator = generator_without_model()
    contexts = [
        RetrievedChunk(
            chunk_id=str(index),
            document_id="doc",
            filename="lecture.pdf",
            subject="subject",
            chapter="chapter",
            page=index,
            content="x" * 120,
            score=1.0,
            semantic_score=1.0,
            lexical_score=1.0,
        )
        for index in range(1, 6)
    ]

    messages, included = generator._build_rag_messages("Câu hỏi?", contexts, 448)

    assert len(included) < len(contexts)
    assert generator._message_token_count(messages) <= 448


def test_vietnamese_finetuned_answer_is_retried_when_it_contains_chinese(monkeypatch) -> None:
    generator = generator_without_model()
    generated = iter([["Triết học是哲学。"], ["Triết học là hệ thống tri thức lý luận."]])
    monkeypatch.setattr(generator, "_generate_messages_batch", lambda *_args, **_kwargs: next(generated))

    answers = generator.generate_without_context_batch(
        ["Triết học là gì?"],
        allowed_sources=[["triethoc.pdf"]],
        strict=True,
        max_new_tokens=64,
        max_input_tokens=448,
    )

    assert answers == ["Triết học là hệ thống tri thức lý luận."]


def test_vietnamese_finetuned_answer_refuses_if_retry_still_contains_chinese(monkeypatch) -> None:
    generator = generator_without_model()
    generated = iter([["Triết học是哲学。"], ["哲学是知识。"]])
    monkeypatch.setattr(generator, "_generate_messages_batch", lambda *_args, **_kwargs: next(generated))

    answers = generator.generate_without_context_batch(
        ["Triết học là gì?"],
        allowed_sources=[["triethoc.pdf"]],
        strict=True,
        max_new_tokens=64,
        max_input_tokens=448,
    )

    assert answers == ["Tôi chưa tìm thấy thông tin này trong tài liệu đã được huấn luyện."]
