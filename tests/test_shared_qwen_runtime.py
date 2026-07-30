from pathlib import Path
from types import MethodType

import pytest

from src.shared_qwen import ANSWER_PROFILE_RULES, SharedQwenRuntime
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
        strict_prompt,
        max_input_tokens,
    ):
        captured.append({
            "question": question,
            "history": history,
            "standalone_query": standalone_query,
            "answer_profile": answer_profile,
            "strict_prompt": strict_prompt,
            "max_input_tokens": max_input_tokens,
        })
        return [{"role": "user", "content": question}], contexts

    runtime._build_rag_messages = MethodType(build_messages, runtime)
    runtime._run_messages = MethodType(
        lambda self, messages, **kwargs: f"answer:{messages[0]['content']}",
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
        "strict_prompt": True,
        "max_input_tokens": 1536,
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
        strict_prompt=False,
        max_input_tokens=10_000,
    )

    assert included == [context]
    assert "**Trả lời trực tiếp:**" in messages[0]["content"]
    assert "**Các lý do chính:**" in messages[0]["content"]
    assert "**Kết luận:**" in messages[0]["content"]
    assert "2-4 gạch đầu dòng" in messages[0]["content"]
    assert "không bắt đầu hoặc kết thúc bằng mẩu câu bị cắt" in messages[0]["content"]
    assert "bỏ bối cảnh lịch sử" in messages[0]["content"]
    assert "không tự viết [1], [2]" in messages[0]["content"]


@pytest.mark.parametrize(
    ("profile", "expected"),
    [
        ("definition", "**Định nghĩa:**"),
        ("list", "danh sách Markdown"),
        ("procedure", "đánh số"),
        ("comparison", "bảng Markdown"),
        ("summary", "5-8 gạch đầu dòng"),
        ("reasoning", "**Kết luận:**"),
    ],
)
def test_answer_profiles_request_adaptive_markdown(profile: str, expected: str) -> None:
    assert expected in ANSWER_PROFILE_RULES[profile]
