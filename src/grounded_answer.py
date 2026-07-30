from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass, replace
from typing import Any, Sequence

from .storage import RetrievedChunk


VIETNAMESE_STOPWORDS = {
    "ai", "bao", "bi", "cac", "cach", "cho", "co", "cua", "duoc", "gi",
    "hay", "khong", "la", "lam", "mot", "nao", "nhung", "o", "tai", "the",
    "theo", "thi", "trong", "tu", "va", "ve",
}

GENERIC_QUESTION_TERMS = {
    "cho", "biet", "noi", "dung", "tai", "lieu", "tom", "tat", "tong", "quan",
    "giai", "thich", "trinh", "bay", "hay", "sao",
}

NOISE_PATTERNS = (
    r"\b(mục lục|tai lieu tham khao|tài liệu tham khảo|câu hỏi ôn tập|"
    r"cau hoi on tap|bài tập ôn tập|bai tap on tap)\b",
    r"(?:\.{4,}|…{3,})\s*\d+\s*$",
)


@dataclass(frozen=True)
class GroundedAnswer:
    answer: str
    used_chunk_ids: list[str]
    support_score: float
    used_fallback: bool
    unsupported_sentences: list[str]
    unsupported_sentence_count: int


def select_context_windows(
    question: str,
    contexts: Sequence[RetrievedChunk],
    *,
    answer_profile: str = "factual",
) -> list[RetrievedChunk]:
    """Keep compact evidence windows and discard common document noise."""
    limits = {
        "definition": 5,
        "factual": 5,
        "short": 5,
        "comparison": 8,
        "list": 8,
        "procedure": 8,
        "reasoning": 8,
        "summary": 12,
    }
    limit = limits.get(answer_profile, 5)
    query_terms = content_terms(question)
    selected: list[RetrievedChunk] = []
    selected_terms: list[set[str]] = []
    seen: set[str] = set()

    for context in contexts:
        if is_noise_text(context.content):
            continue
        sentences = [
            sentence for sentence in split_sentences(context.content)
            if not is_noise_text(sentence)
        ]
        if answer_profile == "reasoning" and not asks_for_historical_context(question):
            sentences = [
                sentence for sentence in sentences
                if not is_historical_background(sentence)
            ]
        if not sentences:
            continue

        if answer_profile == "summary":
            window = sentences[:2]
        else:
            scored = [
                (sentence_relevance(sentence, query_terms), index)
                for index, sentence in enumerate(sentences)
            ]
            _score, anchor = max(scored, key=lambda item: (item[0], -item[1]))
            start = max(0, anchor - 1)
            forward_neighbors = 2 if answer_profile in {
                "comparison", "list", "procedure", "reasoning"
            } else 1
            end = min(len(sentences), anchor + forward_neighbors + 1)
            window = sentences[start:end]

        content = " ".join(window).strip()
        signature = normalize_text(content)[:400]
        if not content or signature in seen:
            continue
        terms = content_terms(content)
        if any(jaccard_similarity(terms, previous) >= 0.82 for previous in selected_terms):
            continue
        seen.add(signature)
        selected_terms.append(terms)
        selected.append(replace(context, content=content))
        if len(selected) >= limit:
            break
    return selected


def asks_for_historical_context(question: str) -> bool:
    normalized = normalize_text(question)
    return any(
        marker in normalized
        for marker in ("lich su", "truong phai", "quan diem cua", "ai la", "tac gia")
    )


def is_historical_background(sentence: str) -> bool:
    normalized = normalize_text(sentence)
    return any(
        marker in normalized
        for marker in (
            "trong lich su",
            "truong phai",
            "cac nha triet hoc",
            "chu nghia duy tam",
            "cac nha duy vat truoc",
            "do khoa hoc chua phat trien",
            "quan diem sieu hinh",
        )
    )


def jaccard_similarity(left: set[str], right: set[str]) -> float:
    union = left | right
    return len(left & right) / len(union) if union else 0.0


def ensure_grounded_answer(
    question: str,
    generated_answer: str,
    contexts: Sequence[RetrievedChunk],
    *,
    minimum_support: float = 0.44,
    embedding_provider: Any | None = None,
) -> GroundedAnswer:
    sentences = split_answer_sentences(generated_answer)
    if not sentences or contains_cjk(generated_answer) or looks_mostly_english(generated_answer):
        return GroundedAnswer("", [], 0.0, True, sentences, len(sentences))
    question_terms = content_terms(question) - GENERIC_QUESTION_TERMS
    answer_terms = content_terms(generated_answer)
    required_overlap = min(2, len(question_terms))
    if required_overlap and len(question_terms & answer_terms) < required_overlap:
        return GroundedAnswer("", [], 0.0, True, sentences, len(sentences))
    key_phrases = question_key_phrases(question)
    if len(key_phrases) >= 2:
        normalized_answer = normalize_text(generated_answer)
        matched_phrases = sum(phrase in normalized_answer for phrase in key_phrases)
        if matched_phrases < 2:
            return GroundedAnswer("", [], 0.0, True, sentences, len(sentences))

    claims_by_sentence = [split_claims(sentence) for sentence in sentences]
    claims = [claim for sentence_claims in claims_by_sentence for claim in sentence_claims]
    semantic_scores = semantic_support_matrix(claims, contexts, embedding_provider)
    supported: list[str] = []
    unsupported: list[str] = []
    used_chunks: list[str] = []
    scores: list[float] = []

    claim_index = 0
    explanatory_question = bool(
        re.search(r"\b(tai sao|vi sao|why)\b", normalize_text(question))
    )
    for sentence, sentence_claims in zip(sentences, claims_by_sentence):
        if (
            has_unrequested_proper_name(sentence, question)
            or (explanatory_question and is_historical_background(sentence))
        ):
            unsupported.extend(sentence_claims)
            claim_index += len(sentence_claims)
            continue
        supported_claims: list[str] = []
        unsupported_claims: list[str] = []
        sentence_chunks: list[RetrievedChunk] = []
        for claim in sentence_claims:
            lexical_score, lexical_chunk = best_lexical_chunk(claim, contexts)
            semantic_chunk, semantic_score = best_semantic_chunk(
                claim_index, contexts, semantic_scores
            )
            claim_index += 1
            chosen = (
                semantic_chunk if semantic_score > lexical_score else lexical_chunk
            )
            combined = (
                lexical_score
                if embedding_provider is None
                else lexical_score * 0.45 + semantic_score * 0.55
            )
            identifiers_ok = critical_identifiers_supported(claim, contexts)
            claim_supported = identifiers_ok and (
                lexical_score >= 0.42
                or semantic_score >= 0.72
                or (semantic_score >= 0.65 and lexical_score >= 0.15)
                or combined >= minimum_support
            )
            scores.append(combined)
            if not claim_supported or chosen is None:
                unsupported_claims.append(claim)
            else:
                supported_claims.append(claim)
                if chosen not in sentence_chunks:
                    sentence_chunks.append(chosen)

        if supported_claims and sentence_chunks:
            supported_text = (
                sentence
                if not unsupported_claims
                else ". ".join(claim.rstrip(".!?") for claim in supported_claims) + "."
            )
            supported.append(supported_text)
            for chosen in sentence_chunks:
                if chosen.chunk_id not in used_chunks:
                    used_chunks.append(chosen.chunk_id)
        unsupported.extend(unsupported_claims)

    support_score = round(
        sum(scores) / len(scores) if scores else 0.0,
        4,
    )
    grounded_text = (
        generated_answer.strip()
        if supported and not unsupported
        else preserve_supported_markdown(generated_answer, supported)
    )
    return GroundedAnswer(
        answer=grounded_text,
        used_chunk_ids=used_chunks,
        support_score=support_score,
        used_fallback=bool(unsupported),
        unsupported_sentences=unsupported,
        unsupported_sentence_count=len(unsupported),
    )


def preserve_supported_markdown(original: str, supported: Sequence[str]) -> str:
    """Keep useful list numbering after unsupported claims have been removed."""
    remaining = list(supported)
    if not remaining:
        return ""

    if re.search(r"^\s*\|.+\|\s*$", original, flags=re.MULTILINE):
        return "\n".join(f"- {sentence}" for sentence in remaining)

    restored: list[str] = []
    prefix_pattern = re.compile(r"^(\s*(?:[-*+]\s+|\d+[.)]\s+|>\s+))(.*)$")
    for raw_line in original.splitlines():
        stripped = raw_line.strip()
        if not stripped:
            continue
        match = prefix_pattern.match(stripped)
        prefix = match.group(1) if match else ""
        content = match.group(2) if match else stripped
        content_terms_set = content_terms(content)
        if not content_terms_set:
            continue

        best_index = -1
        best_score = 0.0
        for index, sentence in enumerate(remaining):
            sentence_terms = content_terms(sentence)
            union = content_terms_set | sentence_terms
            score = len(content_terms_set & sentence_terms) / len(union) if union else 0.0
            if score > best_score:
                best_index = index
                best_score = score
        if best_index >= 0 and best_score >= 0.45:
            restored.append(f"{prefix}{remaining.pop(best_index)}".strip())

    restored.extend(remaining)
    return "\n".join(restored).strip()


def format_grounded_answer(answer: str, answer_profile: str, question: str = "") -> str:
    """Deterministically format verified claims without adding new knowledge."""
    cleaned = (answer or "").strip()
    if not cleaned:
        return ""

    cleaned = re.sub(
        r"\s+(?=\*\*(?:Định nghĩa|Đặc điểm chính|Trả lời trực tiếp|Các lý do chính|Kết luận):\*\*)",
        "\n\n",
        cleaned,
        flags=re.IGNORECASE,
    )
    first_line = cleaned.splitlines()[0].strip()
    if (
        re.search(r"(?m)^\s*(?:[-*+]\s+|\|.+\|\s*$)", cleaned)
        or re.match(r"^\d+[.)]\s+", first_line)
    ):
        return cleaned

    expanded = re.sub(r"\s+(?=\d+[.)]\s+)", "\n", cleaned)
    units: list[str] = []
    for line in expanded.splitlines():
        without_marker = re.sub(r"^\s*\d+[.)]\s+", "", line).strip()
        units.extend(split_answer_sentences(without_marker))
    if len(units) < 2:
        return cleaned

    labels = _format_labels(question)
    if answer_profile == "reasoning":
        direct = units[0]
        details = units[1:5]
        bullets = "\n".join(f"- {item}" for item in details)
        return (
            f"**{labels['direct']}:** {direct}\n\n"
            f"**{labels['reasons']}:**\n{bullets}\n\n"
            f"**{labels['conclusion']}:** {direct}"
        )
    if answer_profile == "definition":
        details = "\n".join(f"- {item}" for item in units[1:5])
        return (
            f"**{labels['definition']}:** {units[0]}\n\n"
            f"**{labels['features']}:**\n{details}"
        )
    if answer_profile == "procedure":
        return "\n".join(f"{index}. {item}" for index, item in enumerate(units[:7], start=1))
    if answer_profile in {"list", "summary", "comparison"}:
        return "\n".join(f"- {item}" for item in units[:8])
    if answer_profile == "factual" and len(units) >= 3:
        details = "\n".join(f"- {item}" for item in units[1:5])
        return f"{units[0]}\n\n{details}"
    return cleaned


def _format_labels(question: str) -> dict[str, str]:
    normalized = normalize_text(question)
    english_markers = {"what", "why", "how", "explain", "compare", "because"}
    english = len(set(re.findall(r"[a-z]+", normalized)) & english_markers) >= 1
    if english:
        return {
            "definition": "Definition",
            "features": "Key points",
            "direct": "Direct answer",
            "reasons": "Main reasons",
            "conclusion": "Conclusion",
        }
    return {
        "definition": "Định nghĩa",
        "features": "Đặc điểm chính",
        "direct": "Trả lời trực tiếp",
        "reasons": "Các lý do chính",
        "conclusion": "Kết luận",
    }


def answer_is_complete(answer: str, answer_profile: str) -> bool:
    """Reject grounded fragments that are too small to answer the requested intent."""
    if not answer or contains_cjk(answer) or looks_mostly_english(answer):
        return False
    sentences = split_answer_sentences(answer)
    word_count = len(content_tokens(answer))
    requirements = {
        "definition": (2, 28),
        "factual": (2, 28),
        "comparison": (3, 45),
        "list": (3, 40),
        "procedure": (3, 40),
        "reasoning": (3, 45),
        "summary": (4, 70),
    }
    minimum_sentences, minimum_words = requirements.get(answer_profile, (2, 28))
    return len(sentences) >= minimum_sentences and word_count >= minimum_words


def answer_is_well_formed(answer: str) -> bool:
    """Catch common small-model degeneration before it reaches the user."""
    if not answer or contains_cjk(answer) or looks_mostly_english(answer):
        return False
    sentences = split_answer_sentences(answer)
    tokens = content_tokens(answer)
    if not sentences or len(tokens) < 4:
        return False
    if answer.count(",") > max(10, len(sentences) * 4):
        return False
    frequencies = {
        token: tokens.count(token)
        for token in set(tokens)
    }
    if max(frequencies.values(), default=0) > max(6, int(len(tokens) * 0.14)):
        return False
    if any(len(content_tokens(sentence)) > 65 for sentence in sentences):
        return False
    return True


def best_lexical_chunk(
    sentence: str,
    contexts: Sequence[RetrievedChunk],
) -> tuple[float, RetrievedChunk | None]:
    terms = content_terms(sentence)
    if not terms:
        return 0.0, None
    ranked = [
        (len(terms & content_terms(chunk.content)) / len(terms), chunk)
        for chunk in contexts
    ]
    return max(ranked, key=lambda item: item[0]) if ranked else (0.0, None)


def semantic_support_matrix(
    sentences: Sequence[str],
    contexts: Sequence[RetrievedChunk],
    embedding_provider: Any | None,
) -> list[list[float]]:
    if embedding_provider is None or not sentences or not contexts:
        return []
    try:
        vectors = embedding_provider.embed_texts(
            list(sentences) + [chunk.content for chunk in contexts]
        )
    except Exception:
        return []
    sentence_vectors = vectors[:len(sentences)]
    context_vectors = vectors[len(sentences):]
    return [
        [
            sum(left * right for left, right in zip(sentence_vector, context_vector))
            for context_vector in context_vectors
        ]
        for sentence_vector in sentence_vectors
    ]


def best_semantic_chunk(
    sentence_index: int,
    contexts: Sequence[RetrievedChunk],
    matrix: Sequence[Sequence[float]],
) -> tuple[RetrievedChunk | None, float]:
    if sentence_index >= len(matrix) or not matrix[sentence_index]:
        return None, 0.0
    scores = matrix[sentence_index]
    best_index = max(range(len(scores)), key=scores.__getitem__)
    return contexts[best_index], scores[best_index]


def critical_identifiers_supported(
    sentence: str,
    contexts: Sequence[RetrievedChunk],
) -> bool:
    evidence = normalize_text(" ".join(chunk.content for chunk in contexts))
    evidence_tokens = set(re.findall(r"[a-z0-9]+", evidence))
    numbers = re.findall(r"\b\d+(?:[.,]\d+)?%?\b", sentence)
    initials = re.findall(r"\b(?:[A-ZĐ]\.){2,}[A-ZĐ]?\b", sentence)
    number_words = re.findall(
        r"\b(?:một|hai|ba|bốn|năm|sáu|bảy|tám|chín|mười)\b",
        sentence.casefold(),
    )
    identifiers = numbers + initials + number_words
    return all(
        normalize_text(identifier) in evidence_tokens
        if re.fullmatch(r"[A-Za-zÀ-ỹĐđ]+", identifier)
        else normalize_text(identifier) in evidence
        for identifier in identifiers
    )


def sentence_relevance(sentence: str, query_terms: set[str]) -> float:
    terms = content_terms(sentence)
    if not terms or not query_terms:
        return 0.0
    return len(terms & query_terms) / len(query_terms)


def is_noise_text(text: str) -> bool:
    normalized = normalize_text(text)
    if any(re.search(pattern, normalized, flags=re.IGNORECASE) for pattern in NOISE_PATTERNS):
        return True
    return text.count("?") >= 2 or len(content_terms(text)) < 3


def split_answer_sentences(text: str) -> list[str]:
    lines = (text or "").splitlines()
    filtered_lines = []
    for index, line in enumerate(lines):
        stripped = line.strip()
        if not stripped or re.fullmatch(r"\|?(?:\s*:?-+:?\s*\|)+", stripped):
            continue
        next_line = lines[index + 1].strip() if index + 1 < len(lines) else ""
        if next_line and re.fullmatch(r"\|?(?:\s*:?-+:?\s*\|)+", next_line):
            continue
        filtered_lines.append(stripped)
    cleaned = "\n".join(filtered_lines)
    parts = re.split(r"(?<=[.!?;])\s+|\n+", cleaned)
    return [part.strip(" -\t") for part in parts if len(part.strip()) >= 12]


def split_claims(sentence: str) -> list[str]:
    claims = [
        part.strip()
        for part in re.split(r"[,;]\s+", sentence)
        if len(part.strip()) >= 12
    ]
    return claims or [sentence]


def split_sentences(text: str) -> list[str]:
    cleaned = clean_ocr_text(text)
    if not cleaned:
        return []
    protected = re.sub(
        r"\b(?:[A-ZĐ]\.){2,}",
        lambda match: match.group(0).replace(".", "\u2024"),
        cleaned,
    )
    parts = [
        part.replace("\u2024", ".").strip(" -\t")
        for part in re.split(r"(?<=[.!?;:])\s+|\n+", protected)
    ]
    return [part for part in parts if len(part) >= 18]


def content_terms(text: str) -> set[str]:
    return set(content_tokens(text))


def content_tokens(text: str) -> list[str]:
    normalized = normalize_text(text)
    return [
        token
        for token in re.findall(r"[a-z0-9]+", normalized)
        if (len(token) > 1 or token == "y") and token not in VIETNAMESE_STOPWORDS
    ]


def question_key_phrases(question: str) -> list[str]:
    tokens = [
        token for token in content_tokens(question)
        if token not in GENERIC_QUESTION_TERMS
    ]
    return [
        f"{left} {right}"
        for left, right in zip(tokens, tokens[1:])
        if left != right
    ]


def has_unrequested_proper_name(sentence: str, question: str) -> bool:
    normalized_question = normalize_text(question)
    if not re.search(r"\b(tai sao|vi sao|why)\b", normalized_question):
        return False
    words = re.findall(r"\b[A-ZĐ][a-zà-ỹđ]{2,}\b", sentence)
    sentence_openers = {
        "chu", "do", "doi", "khi", "nguoc", "nhu", "theo", "trong", "tuy", "vat", "viec",
    }
    if words and normalize_text(words[0]) in sentence_openers:
        words = words[1:]
    return any(normalize_text(word) not in normalized_question for word in words)


def normalize_text(text: str) -> str:
    decomposed = unicodedata.normalize("NFD", (text or "").casefold())
    without_marks = "".join(
        char for char in decomposed if unicodedata.category(char) != "Mn"
    )
    return without_marks.replace("đ", "d")


def clean_ocr_text(text: str) -> str:
    return re.sub(r"\s+", " ", text or "").strip()


def contains_cjk(text: str) -> bool:
    return bool(re.search(r"[\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uac00-\ud7af]", text or ""))


def looks_mostly_english(text: str) -> bool:
    normalized = normalize_text(text)
    english = {
        "and", "are", "because", "for", "from", "include", "is", "of", "that",
        "the", "they", "this", "to", "with",
    }
    vietnamese = {
        "ban", "cai", "chat", "chu", "duoc", "giua", "hoc", "khach", "nghia",
        "nhan", "phat", "quan", "thuc", "triet", "vat",
    }
    tokens = set(re.findall(r"[a-z]+", normalized))
    return len(tokens & english) >= 3 and not (tokens & vietnamese)
