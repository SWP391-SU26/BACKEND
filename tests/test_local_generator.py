from src.local_generator import QWEN_CHAT_TEMPLATE, ensure_chat_template


class TokenizerStub:
    def __init__(self, chat_template=None):
        self.chat_template = chat_template


def test_missing_chat_template_uses_qwen_chatml_fallback() -> None:
    tokenizer = TokenizerStub()

    ensure_chat_template(tokenizer)

    assert tokenizer.chat_template == QWEN_CHAT_TEMPLATE
    assert "<|im_start|>" in tokenizer.chat_template
    assert "add_generation_prompt" in tokenizer.chat_template


def test_existing_chat_template_is_preserved() -> None:
    tokenizer = TokenizerStub("custom-template")

    ensure_chat_template(tokenizer)

    assert tokenizer.chat_template == "custom-template"
