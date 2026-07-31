from pathlib import Path
from types import MethodType

import pytest

from src.shared_qwen import (
    ANSWER_DEPTH_RULES,
    ANSWER_PROFILE_RULES,
    BatchTelemetry,
    SharedQwenRuntime,
)
from src.storage import RetrievedChunk


def test_batch_uses_standalone_query_and_answer_profile() -> None:
    runtime = SharedQwenRuntime.__new__(SharedQwenRuntime)
    captured = []

    def build_messages(
        self,
        question,
        contexts,
        *,
        history,
        standalone_query,
        answer_profile,
        answer_depth,
        strict_prompt,
        max_input_tokens,
    ):
        captured.append({
            "question": question,
            "history": history,
            "standalone_query": standalone_query,
            "answer_profile": answer_profile,
            "answer_depth": answer_depth,
            "strict_prompt": strict_prompt,
            "max_input_tokens": max_input_tokens,
        })
        return [{"role": "user", "content": question}], contexts

    runtime._build_rag_messages = MethodType(build_messages, runtime)
    runtime._run_messages_batch = MethodType(
        lambda self, messages, **kwargs: (
            [f"answer:{item[0]['content']}" for item in messages],
            BatchTelemetry(len(messages), len(messages), 0),
        ),
        runtime,
    )

    results = runtime.generate_batch(
        [("Tại sao điều đó quan trọng?", ["chunk"], "Vai trò của vật chất là gì?", "reasoning")],
        max_new_tokens=192,
        max_input_tokens=1536,
    )

    assert results == [("answer:Tại sao điều đó quan trọng?", ["chunk"])]
    assert captured == [{
        "question": "Tại sao điều đó quan trọng?",
        "history": [],
        "standalone_query": "Vai trò của vật chất là gì?",
        "answer_profile": "reasoning",
        "answer_depth": "STANDARD",
        "strict_prompt": True,
        "max_input_tokens": 1280,
    }]


def test_rewrite_query_never_enables_adapter() -> None:
    runtime = SharedQwenRuntime.__new__(SharedQwenRuntime)
    captured = {}

    def run_messages(self, messages, **kwargs):
        captured.update(kwargs)
        captured["prompt"] = messages[0]["content"]
        return "Vai trò của vật chất đối với ý thức là gì?"

    runtime._run_messages = MethodType(run_messages, runtime)

    rewritten = runtime.rewrite_query(
        "Tại sao điều đó quan trọng?",
        history=[{"role": "assistant", "content": "Vật chất quyết định ý thức."}],
        intent="reasoning",
        attempt=2,
        evidence_hints=["Vật chất tồn tại khách quan."],
    )

    assert rewritten == "Vai trò của vật chất đối với ý thức là gì?"
    assert captured["use_adapter"] is False
    assert "Vật chất quyết định ý thức." in captured["prompt"]
    assert "Vật chất tồn tại khách quan." in captured["prompt"]


def test_unverified_adapter_requires_explicit_acknowledgement(tmp_path: Path) -> None:
    for filename in ("adapter_config.json", "adapter_model.safetensors", "training_manifest.json"):
        (tmp_path / filename).write_text("{}", encoding="utf-8")
    runtime = SharedQwenRuntime.__new__(SharedQwenRuntime)
    runtime.adapter_dir = tmp_path
    runtime.base_model = "Qwen/Qwen2.5-1.5B-Instruct"
    runtime._manifest = {
        "base_model": runtime.base_model,
        "quality_gate": {"passed": False},
    }

    with pytest.raises(RuntimeError, match="quality gate"):
        runtime._validate_adapter(allow_unverified=False)

    runtime._validate_adapter(allow_unverified=True)


def test_reasoning_prompt_requires_a_complete_study_answer() -> None:
    class Tokenizer:
        def apply_chat_template(self, messages, **_kwargs):
            return list(range(sum(len(item["content"]) for item in messages)))

    runtime = SharedQwenRuntime.__new__(SharedQwenRuntime)
    runtime.tokenizer = Tokenizer()
    context = RetrievedChunk(
        chunk_id="chunk-1",
        document_id="document-1",
        filename="triethoc.pdf",
        subject="Triết học",
        chapter="Chương 2",
        page=88,
        content="Vật chất có trước và ý thức có sau.",
        score=0.9,
        semantic_score=0.9,
        lexical_score=0.8,
    )

    messages, included = runtime._build_rag_messages(
        "Tại sao vật chất quyết định ý thức?",
        [context],
        history=[],
        standalone_query=None,
        answer_profile="reasoning",
        answer_depth="STANDARD",
        strict_prompt=False,
        max_input_tokens=10_000,
    )

    assert included == [context]
    assert "Mức độ STANDARD" in messages[0]["content"]
    assert "các lý do khác nhau" in messages[0]["content"]
    assert "không bắt đầu hoặc kết thúc bằng mẩu câu bị cắt" in messages[0]["content"]
    assert "bỏ bối cảnh lịch sử" in messages[0]["content"]
    assert "không tự viết [1], [2]" in messages[0]["content"]


@pytest.mark.parametrize(
    "profile",
    ["definition", "list", "procedure", "comparison", "summary", "reasoning"],
)
def test_answer_profiles_only_control_structure(profile: str) -> None:
    assert ANSWER_PROFILE_RULES[profile]
    assert "khoảng" not in ANSWER_PROFILE_RULES[profile]


def test_answer_depth_rules_have_distinct_targets() -> None:
    assert "40-100" in ANSWER_DEPTH_RULES["SHORT"]
    assert "140-240" in ANSWER_DEPTH_RULES["STANDARD"]
    assert "280-450" in ANSWER_DEPTH_RULES["DEEP"]


def test_oom_fallback_preserves_answer_order() -> None:
    runtime = SharedQwenRuntime.__new__(SharedQwenRuntime)
    runtime._clear_cuda_cache = MethodType(lambda self: None, runtime)
    calls = []

    def generate_once(self, messages, **_kwargs):
        calls.append(len(messages))
        if len(messages) > 2:
            raise RuntimeError("CUDA out of memory")
        return [item[0]["content"] for item in messages]

    runtime._generate_messages_once = MethodType(generate_once, runtime)
    messages = [
        [{"role": "user", "content": f"q{index}"}]
        for index in range(4)
    ]

    answers, effective_size, fallbacks = runtime._generate_with_oom_fallback(
        messages,
        use_adapter=False,
        max_input_tokens=1024,
        max_new_tokens=128,
        max_time_seconds=None,
    )

    assert answers == ["q0", "q1", "q2", "q3"]
    assert effective_size == 2
    assert fallbacks == 1
    assert calls == [4, 2, 2]


def test_depth_groups_restore_original_order() -> None:
    runtime = SharedQwenRuntime.__new__(SharedQwenRuntime)

    def build_messages(self, question, contexts, **_kwargs):
        return [{"role": "user", "content": question}], contexts

    runtime._build_rag_messages = MethodType(build_messages, runtime)
    runtime._run_messages_batch = MethodType(
        lambda self, messages, **_kwargs: (
            [f"answer:{item[0]['content']}" for item in messages],
            BatchTelemetry(len(messages), len(messages), 0),
        ),
        runtime,
    )

    results, telemetry = runtime.generate_batch_with_telemetry(
        [
            ("short", ["c1"], "short", "definition", "SHORT"),
            ("deep", ["c2"], "deep", "summary", "DEEP"),
            ("standard", ["c3"], "standard", "factual", "STANDARD"),
        ],
        max_new_tokens=192,
        max_input_tokens=1536,
    )

    assert [answer for answer, _contexts in results] == [
        "answer:short",
        "answer:deep",
        "answer:standard",
    ]
    assert telemetry.requested_batch_size == 3


def test_grounding_repairs_are_generated_in_one_batch() -> None:
    runtime = SharedQwenRuntime.__new__(SharedQwenRuntime)
    captured = []
    runtime._run_messages_batch = MethodType(
        lambda self, messages, **_kwargs: (
            captured.extend(messages) or [
                f"repair-{index}" for index, _messages in enumerate(messages)
            ],
            BatchTelemetry(len(messages), len(messages), 0),
        ),
        runtime,
    )
    context = RetrievedChunk(
        chunk_id="chunk-1",
        document_id="document-1",
        filename="lesson.pdf",
        subject="Subject",
        chapter="Chapter",
        page=2,
        content="Evidence from the uploaded lesson.",
        score=0.9,
        semantic_score=0.9,
        lexical_score=0.8,
    )

    answers, telemetry = runtime.repair_unsupported_sentences_batch(
        [
            ("Question 1", ["Unsupported 1"], [context]),
            ("Question 2", ["Unsupported 2"], [context]),
        ],
        max_input_tokens=1536,
        max_new_tokens=192,
    )

    assert answers == ["repair-0", "repair-1"]
    assert len(captured) == 2
    assert telemetry == BatchTelemetry(2, 2, 0)


def test_incomplete_answers_are_rewritten_in_one_batch() -> None:
    runtime = SharedQwenRuntime.__new__(SharedQwenRuntime)
    captured = []
    runtime._run_messages_batch = MethodType(
        lambda self, messages, **_kwargs: (
            captured.extend(messages) or [
                f"complete-{index}" for index, _messages in enumerate(messages)
            ],
            BatchTelemetry(len(messages), len(messages), 0),
        ),
        runtime,
    )
    context = RetrievedChunk(
        chunk_id="chunk-1",
        document_id="document-1",
        filename="manual.pdf",
        subject="Subject",
        chapter="Chapter",
        page=4,
        content="An explicit definition with every required clause.",
        score=0.9,
        semantic_score=0.9,
        lexical_score=0.8,
    )

    answers, telemetry = runtime.complete_grounded_answer_batch(
        [
            (
                "Khái niệm này là gì?",
                "Khái niệm này là",
                [context],
                "definition",
                "SHORT",
                ["Câu cuối chưa kết thúc hoàn chỉnh."],
            ),
            (
                "Nêu quy trình.",
                "1. Bước đầu",
                [context],
                "procedure",
                "STANDARD",
                ["Danh sách có một mục đang dang dở."],
            ),
        ],
        max_input_tokens=1536,
        max_new_tokens=160,
    )

    assert answers == ["complete-0", "complete-1"]
    assert len(captured) == 2
    assert "preserve every essential clause" in captured[0][0]["content"]
    assert telemetry == BatchTelemetry(2, 2, 0)
