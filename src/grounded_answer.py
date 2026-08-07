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


@dataclass(frozen=True)
class ExplicitDefinitionEvidence:
    answer: str
    used_chunk_ids: list[str]


def extract_historical_origin(
    question: str,
    contexts: Sequence[RetrievedChunk],
) -> ExplicitDefinitionEvidence | None:
    """Extract a direct where/when statement instead of letting generation invent one."""
    normalized_question = normalize_text(question)
    if "ra doi" not in normalized_question or not any(
        marker in normalized_question
        for marker in ("o dau", "khi nao", "som nhat", "thoi gian", "where", "when")
    ):
        return None

    subject_text = normalized_question.split("ra doi", 1)[0]
    raw_subject_match = re.search(r"^(.*?)\s+ra\s+đời\b", question, flags=re.I)
    raw_subject = raw_subject_match.group(1).strip() if raw_subject_match else ""
    subject_terms = (
        content_terms(subject_text)
        - VIETNAMESE_STOPWORDS
        - GENERIC_QUESTION_TERMS
    )
    best: tuple[float, str, str] | None = None
    for context in contexts:
        for sentence in split_sentences(context.content or ""):
            normalized_sentence = normalize_text(sentence)
            if "ra doi" not in normalized_sentence:
                continue
            sentence_terms = content_terms(sentence)
            if subject_terms and not subject_terms.issubset(sentence_terms):
                continue
            has_place = any(
                marker in normalized_sentence
                for marker in (
                    "phuong dong", "phuong tay", "trung quoc", "an do", "hy lap",
                    "tai ", "o ca ",
                )
            )
            has_time = any(
                marker in normalized_sentence
                for marker in (
                    "the ky", "truoc cong nguyen", "cung mot thoi gian", "khoang ",
                )
            )
            if not (has_place or has_time):
                continue
            if raw_subject:
                direct_start = re.search(
                    rf"{re.escape(raw_subject)}\s+ra\s+đời\b",
                    sentence,
                    flags=re.I,
                )
                if direct_start is not None:
                    sentence = sentence[direct_start.start():]
            score = 1.0 + float(has_place) + float(has_time)
            if "ra doi o ca" in normalized_sentence:
                score += 1.0
            if best is None or score > best[0]:
                best = (score, " ".join(sentence.split()), context.chunk_id)

    if best is None:
        return None
    return ExplicitDefinitionEvidence(answer=best[1], used_chunk_ids=[best[2]])


def extract_explicit_definition(
    question: str,
    contexts: Sequence[RetrievedChunk],
) -> ExplicitDefinitionEvidence | None:
    """Return an exact quoted definition when the selected evidence contains one."""
    normalized_question = normalize_text(question)
    asks_for_components = any(
        marker in normalized_question
        for marker in (
            "gom nhung",
            "bao gom",
            "gom may",
            "may mat",
            "nhung mat nao",
            "thanh phan",
            "cac phan",
            "cac buoc",
        )
    )
    query_terms = (
        content_terms(question)
        - VIETNAMESE_STOPWORDS
        - GENERIC_QUESTION_TERMS
        - {"dinh", "nghia", "khai", "niem", "nhu", "what", "defined", "definition"}
    )
    if not contexts or not query_terms:
        return None

    grouped: dict[str, list[RetrievedChunk]] = {}
    for context in contexts:
        grouped.setdefault(context.document_id, []).append(context)

    best: tuple[float, str, list[str]] | None = None
    best_components: tuple[float, list[tuple[str, str]], list[str]] | None = None
    attribution_prefix = question.split(",", 1)[0] if "," in question else ""
    attribution_terms = (
        content_terms(attribution_prefix)
        - VIETNAMESE_STOPWORDS
        - GENERIC_QUESTION_TERMS
        - {"ph", "mr", "mrs", "dr", "professor"}
    )
    for document_contexts in grouped.values():
        ordered = sorted(
            document_contexts,
            key=lambda item: (item.page if item.page is not None else 10**9, item.chunk_id),
        )
        combined_parts: list[str] = []
        ranges: list[tuple[int, int, str]] = []
        cursor = 0
        for context in ordered:
            content = " ".join((context.content or "").split())
            content = re.sub(r"\s+\d{1,4}\s*$", "", content)
            if not content:
                continue
            if combined_parts:
                combined_parts.append("\n")
                cursor += 1
            start = cursor
            combined_parts.append(content)
            cursor += len(content)
            ranges.append((start, cursor, context.chunk_id))
        combined = "".join(combined_parts)
        if asks_for_components:
            component_pattern = re.compile(
                r"\b(M\u1eb7t|Ph\u1ea7n|B\u01b0\u1edbc|Mat|Phan|Buoc)\s+"
                r"(?:th\u1ee9|thu)\s+"
                r"(nh\u1ea5t|hai|ba|t\u01b0|tu|b\u1ed1n|bon)\s*:",
                flags=re.I,
            )
            component_matches = list(component_pattern.finditer(combined))
            extracted_components: list[tuple[str, str]] = []
            component_ids: list[str] = []
            for index, component_match in enumerate(component_matches):
                value_start = component_match.end()
                value_end = (
                    component_matches[index + 1].start()
                    if index + 1 < len(component_matches)
                    else min(len(combined), value_start + 500)
                )
                value = " ".join(combined[value_start:value_end].split()).strip()
                sentence_match = re.match(r"(.{10,360}?[.!?])(?:\s|$)", value)
                if sentence_match:
                    value = sentence_match.group(1).strip()
                if len(content_terms(value)) < 3:
                    continue
                label = " ".join(component_match.group(0).rstrip(":").split())
                extracted_components.append((label, value))
                component_ids.extend(
                    chunk_id
                    for start, end, chunk_id in ranges
                    if start < value_end and end > component_match.start()
                )
            if len(extracted_components) >= 2:
                unique_component_ids = list(dict.fromkeys(component_ids))
                component_score = len(extracted_components) + len(unique_component_ids) / 10
                if best_components is None or component_score > best_components[0]:
                    best_components = (
                        component_score,
                        extracted_components,
                        unique_component_ids,
                    )

        if attribution_terms:
            attribution_pattern = re.compile(
                r"(?:Theo|According to)\s+([^:\n]{2,100})\s*:\s*"
                r"(?:\"|\u201c)(.{20,700}?)(?:\"|\u201d)",
                flags=re.I | re.S,
            )
            for match in attribution_pattern.finditer(combined):
                cited_name_terms = content_terms(match.group(1))
                if not attribution_terms.issubset(cited_name_terms):
                    continue
                quote = " ".join(match.group(2).split()).strip()
                if not 5 <= len(content_terms(quote)) <= 100:
                    continue
                used_ids = [
                    chunk_id
                    for start, end, chunk_id in ranges
                    if start < match.end() and end > match.start()
                ]
                overlap = len(query_terms & content_terms(quote)) / max(1, len(query_terms))
                score = 2.0 + overlap
                if used_ids and (best is None or score > best[0]):
                    best = (score, quote, used_ids)

        definition_patterns = (
            (
                re.compile(
                    r"(?:khái quát lại\s*,?\s*)?(?:có thể hiểu|được hiểu)\s*:\s*"
                    r"(.{30,700}?[.!?])(?:\s|$)",
                    flags=re.I | re.S,
                ),
                True,
                False,
            ),
            (
                re.compile(
                    r"(?:định nghĩa|dinh nghia|definition|defined as)\s*:\s*[\"“](.{30,700}?)[\"”]",
                    flags=re.I | re.S,
                ),
                True,
                False,
            ),
            (
                re.compile(
                    r"(?:^|[.!?:]\s*|[-•]\s+)([^.!?\n]{2,100}\s+(?:là|la|is)\s+.{20,500}?[.!?])",
                    flags=re.I | re.S,
                ),
                False,
                True,
            ),
            (re.compile(r"[\"“](.{30,700}?)[\"”]", flags=re.S), False, False),
        )
        for pattern, direct_definition, requires_subject_match in definition_patterns:
            for match in pattern.finditer(combined):
                quote = " ".join(match.group(1).split()).strip()
                quote_terms = content_terms(quote)
                if (
                    len(quote_terms) < 12
                    or len(quote_terms) > 90
                    or re.match(r"^\d+\s*\(", quote)
                ):
                    continue
                overlap = len(query_terms & quote_terms) / max(1, len(query_terms))
                prefix = normalize_text(combined[max(0, match.start() - 260):match.start()])
                has_definition_cue = direct_definition or any(
                    cue in prefix
                    for cue in (
                        "dinh nghia",
                        "khai niem",
                        "duoc hieu",
                        "definition",
                        "defined as",
                    )
                )
                if overlap < 0.25 or not has_definition_cue:
                    continue
                used_ids = [
                    chunk_id
                    for start, end, chunk_id in ranges
                    if start < match.end() and end > match.start()
                ]
                if not used_ids:
                    continue
                score = overlap + min(len(quote_terms), 80) / 400
                normalized_quote = normalize_text(quote)
                subject_match = re.match(r"^(.{1,120}?)\s+(?:la|is)\s+", normalized_quote)
                subject_overlap = 0.0
                if subject_match:
                    subject_terms = (
                        content_terms(subject_match.group(1))
                        - VIETNAMESE_STOPWORDS
                        - GENERIC_QUESTION_TERMS
                    )
                    if subject_terms:
                        subject_overlap = len(query_terms & subject_terms) / len(subject_terms)
                        score += subject_overlap * 1.2
                        if subject_overlap >= 0.75 and len(subject_terms) <= 4:
                            score += 0.4
                if requires_subject_match and (
                    subject_match is None or subject_overlap < 0.50
                ):
                    continue
                if direct_definition:
                    score += 1.0
                if best is None or score > best[0]:
                    best = (score, quote, used_ids)

    if asks_for_components:
        if best is None or best_components is None:
            return None
        component_lines = [
            f"{index}. **{label}:** {value}"
            for index, (label, value) in enumerate(best_components[1], start=1)
        ]
        return ExplicitDefinitionEvidence(
            answer=(
                f"**Ph\u00e1t bi\u1ec3u:** \u201c{best[1]}\u201d\n\n"
                f"**C\u00e1c m\u1eb7t/ph\u1ea7n ch\u00ednh:**\n"
                + "\n".join(component_lines)
            ),
            used_chunk_ids=list(dict.fromkeys(best[2] + best_components[2])),
        )

    if best is None:
        return None
    return ExplicitDefinitionEvidence(
        answer=f"**Định nghĩa:** “{best[1]}”",
        used_chunk_ids=best[2],
    )


def select_context_windows(
    question: str,
    contexts: Sequence[RetrievedChunk],
    *,
    answer_profile: str = "factual",
    answer_depth: str = "STANDARD",
) -> list[RetrievedChunk]:
    """Keep compact evidence windows and discard common document noise."""
    depth_limits = {"SHORT": 5, "STANDARD": 8, "DEEP": 12}
    limit = depth_limits.get((answer_depth or "STANDARD").upper(), 8)
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
                if (
                    not is_historical_background(sentence)
                    and not is_methodology_guidance(sentence)
                )
            ]
        if not sentences:
            continue

        if (
            (answer_depth or "STANDARD").upper() == "DEEP"
            and answer_profile in {"list", "comparison", "summary"}
        ):
            window = sentences[:8]
        elif answer_profile == "summary":
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
            if (answer_depth or "STANDARD").upper() == "DEEP":
                forward_neighbors += 1
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


def is_methodology_guidance(sentence: str) -> bool:
    """Identify practical lessons that must not be presented as causal evidence."""
    normalized = normalize_text(sentence)
    return any(
        marker in normalized
        for marker in (
            "y nghia phuong phap luan",
            "bai hoc rut ra",
            "con nguoi phai ton trong",
            "phai ton trong khach quan",
            "phat huy tinh nang dong chu quan",
            "trong hoat dong thuc tien can",
            "trong hoat dong nhan thuc can",
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
    answer_profile: str = "factual",
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
    if len(key_phrases) >= 2 and answer_profile not in {
        "list", "summary", "comparison", "procedure"
    }:
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
            or (
                answer_profile == "list"
                and not list_item_label_supported(sentence, contexts)
            )
        ):
            unsupported.extend(sentence_claims)
            claim_index += len(sentence_claims)
            continue
        supported_claims: list[str] = []
        unsupported_claims: list[str] = []
        sentence_chunks: list[RetrievedChunk] = []
        for claim in sentence_claims:
            if explanatory_question and (
                is_methodology_guidance(claim)
                or not claim_explains_requested_relation(question, claim)
            ):
                unsupported_claims.append(claim)
                claim_index += 1
                continue
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

        if answer_profile in {
            "definition", "list", "summary", "comparison", "procedure"
        } and unsupported_claims:
            # Removing one clause from a structured point can change its meaning
            # or leave a dangling fragment. Let the repair pass rewrite it whole.
            unsupported.extend(sentence_claims)
            continue

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


def claim_explains_requested_relation(question: str, claim: str) -> bool:
    """Keep causal claims aligned with the direction explicitly asked by the user."""
    normalized_question = normalize_text(question)
    relation = re.search(
        r"(?:tai sao|vi sao|why)\s+(.{1,80}?)\s+quyet dinh\s+(.+?)(?:\?|$)",
        normalized_question,
    )
    if relation is None:
        return True

    subject_terms = (
        content_terms(relation.group(1))
        - GENERIC_QUESTION_TERMS
        - VIETNAMESE_STOPWORDS
    )
    normalized_claim = normalize_text(claim)
    claim_terms = content_terms(claim)
    subject_present = bool(subject_terms & claim_terms)
    causal_marker_present = any(
        marker in normalized_claim
        for marker in (
            "quyet dinh",
            "nguon goc",
            "co truoc",
            "sinh ra",
            "quy dinh",
            "phan anh",
            "tac dong len",
            "nen tang",
            "dieu kien",
            "hinh thanh",
        )
    )
    return subject_present and causal_marker_present


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
    cleaned = _deduplicate_markdown_items(cleaned)
    cleaned = _sanitize_named_entity_list(cleaned, question)
    profile = (answer_profile or "factual").strip().lower()
    if profile == "reasoning":
        cleaned = _polish_reasoning_markdown(cleaned, question)
    if profile == "definition":
        return _format_definition(cleaned, question)

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
    if profile == "reasoning":
        direct = units[0]
        details = units[1:]
        bullets = "\n".join(f"- {item}" for item in details)
        return (
            f"**{labels['direct']}:** {direct}\n\n"
            f"**{labels['reasons']}:**\n{bullets}"
        )
    if profile == "procedure":
        return "\n".join(f"{index}. {item}" for index, item in enumerate(units, start=1))
    if profile in {"list", "summary", "comparison"}:
        return "\n".join(f"- {item}" for item in units)
    return cleaned


def _polish_reasoning_markdown(answer: str, question: str) -> str:
    """Remove repeated fragments from small-model reasoning without adding facts."""
    marker = re.compile(r"^(\s*[-*+]\s+)(.*)$")
    direct_terms: set[str] = set()
    bullets: list[tuple[str, set[str]]] = []
    output: list[str] = []

    for line in answer.splitlines():
        match = marker.match(line)
        if match is None:
            output.append(line)
            if line.strip() and not line.strip().startswith("**Các lý do"):
                direct_terms |= content_terms(line.replace("**", ""))
            continue

        content = match.group(2).strip().strip("\"'“”").strip()
        content = re.sub(r"^(?:và|đồng thời|bên cạnh đó)\s+", "", content, flags=re.I)
        if not content:
            continue
        terms = content_terms(content)
        if not terms:
            continue
        if direct_terms and terms <= direct_terms:
            continue
        if any(jaccard_similarity(terms, previous) >= 0.72 for _, previous in bullets):
            continue
        content = _ensure_sentence_end(content)
        bullets.append((content, terms))
        output.append(f"{match.group(1)}{content}")

    if len(bullets) < 2:
        normalized_question = normalize_text(question)
        note = (
            "_Các đoạn được truy xuất chỉ cung cấp trực tiếp luận cứ trên; "
            "tài liệu chưa đủ bằng chứng để tách thêm các khía cạnh độc lập._"
            if any(
                token in normalized_question
                for token in ("tai sao", "vi sao", "giai thich")
            )
            else ""
        )
        if note:
            output.extend(["", note])
    return "\n".join(output).strip()


def _deduplicate_markdown_items(answer: str) -> str:
    """Remove repeated list points while preserving the model's Markdown order."""
    seen_terms: list[set[str]] = []
    output: list[str] = []
    marker = re.compile(r"^(\s*(?:[-*+]\s+|\d+[.)]\s+))(.*)$")
    for line in answer.splitlines():
        match = marker.match(line)
        if not match:
            output.append(line)
            continue
        terms = content_terms(match.group(2))
        if terms and any(
            jaccard_similarity(terms, previous) >= 0.86
            for previous in seen_terms
        ):
            continue
        if terms:
            seen_terms.append(terms)
        output.append(line)
    return "\n".join(output).strip()


def _sanitize_named_entity_list(answer: str, question: str) -> str:
    """Keep named items when a small model pads a requested entity list with background."""
    normalized_question = normalize_text(question)
    if "hoc thuyet" not in normalized_question:
        return answer

    marker = re.compile(r"^(\s*(?:[-*+]\s+|\d+[.)]\s+))(.*)$")
    output: list[str] = []
    kept_items = 0
    for line in answer.splitlines():
        match = marker.match(line)
        if not match:
            output.append(line)
            continue
        prefix, content = match.groups()
        parts = [part.strip() for part in content.split(":")]
        if len(parts) >= 3:
            final_clause = parts[-1]
            entity_match = re.match(
                r"(?P<name>[\wÀ-ỹĐđ][\wÀ-ỹĐđ -]{1,45}?)\s+là\b",
                final_clause,
                flags=re.IGNORECASE,
            )
            if entity_match:
                name = entity_match.group("name").strip(" -*")
                content = f"**{name}:** {final_clause}"

        label = content.split(":", 1)[0].replace("**", "").strip()
        normalized_label = normalize_text(label)
        if normalized_label.startswith("hoan canh") or normalized_label.startswith(
            "triet hoc trung hoa"
        ):
            continue
        output.append(f"{prefix}{content}")
        kept_items += 1

    if 0 < kept_items < 3:
        output.extend([
            "",
            "_Tài liệu được truy xuất hiện chỉ nêu rõ các học thuyết trên; "
            "không có đủ bằng chứng để liệt kê thêm._",
        ])
    return "\n".join(output).strip()


def _format_definition(answer: str, question: str) -> str:
    """Join verified definition clauses into a coherent paragraph."""
    plain = re.sub(
        r"\*\*(?:Định nghĩa|Definition|Đặc điểm chính|Key points):\*\*\s*",
        "",
        answer,
        flags=re.IGNORECASE,
    )
    plain = re.sub(r"(?m)^\s*(?:[-*+]\s+|\d+[.)]\s+)", "", plain)
    units = split_answer_sentences(plain)
    if not units:
        return answer.strip()

    merged: list[str] = []
    continuation = re.compile(
        r"^(?:và|về|đồng thời|trong đó|bao gồm|qua đó|từ đó)\b",
        flags=re.IGNORECASE,
    )
    for unit in units:
        unit = re.sub(
            r"\b([\wÀ-ỹĐđ]+)\s+và\s+\1\b",
            r"\1",
            unit,
            flags=re.IGNORECASE,
        ).strip()
        if merged and (continuation.match(unit) or unit[:1].islower()):
            merged[-1] = merged[-1].rstrip(".;:, ") + ", " + unit
        else:
            merged.append(unit)

    paragraph = " ".join(_ensure_sentence_end(unit) for unit in merged).strip()
    return f"**{_format_labels(question)['definition']}:** {paragraph}"


def _ensure_sentence_end(text: str) -> str:
    cleaned = text.strip()
    return cleaned if not cleaned or cleaned[-1] in ".!?" else cleaned + "."


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


def answer_completeness_issues(
    answer: str,
    answer_profile: str,
    answer_depth: str = "STANDARD",
    evidence_count: int | None = None,
) -> list[str]:
    """Describe missing coverage without asking the model to invent unsupported points."""
    issues: list[str] = []
    if not answer or contains_cjk(answer) or looks_mostly_english(answer):
        return ["Câu trả lời rỗng hoặc sai ngôn ngữ."]

    stripped = answer.rstrip()
    profile = (answer_profile or "factual").lower()
    depth = (answer_depth or "STANDARD").upper()
    if stripped.endswith((": ", ":", ";", ",", "-", "•")):
        issues.append("Câu trả lời kết thúc giữa một ý.")
    last_line = stripped.splitlines()[-1].strip()
    if re.fullmatch(r"(?:[-*+]|\d+[.)])", last_line):
        issues.append("Danh sách có một mục đang dang dở.")
    if profile != "comparison" and stripped[-1:] not in ".?!":
        issues.append("Câu cuối chưa kết thúc hoàn chỉnh.")

    sentences = split_answer_sentences(answer)
    word_count = len(content_tokens(answer))
    minimum_words = {
        "SHORT": 18,
        "STANDARD": 45,
        "DEEP": 150,
    }.get(depth, 45)
    available_evidence = max(1, evidence_count or 1)
    if depth == "DEEP" and available_evidence <= 2:
        minimum_words = 55
    elif depth == "DEEP" and available_evidence <= 4:
        minimum_words = 80
    if word_count < minimum_words:
        issues.append(
            f"Câu trả lời mới có {word_count} từ, chưa đủ độ sâu {depth.lower()}."
        )

    coverage_targets = {
        "list": 3,
        "reasoning": 3,
        "comparison": 2,
        "summary": 5,
        "procedure": 3,
    }
    target = coverage_targets.get(profile, 1)
    target = min(target, available_evidence)
    markdown_points = len(re.findall(r"(?m)^\s*(?:[-*+]\s+|\d+[.)]\s+)", answer))
    markdown_contents = re.findall(
        r"(?m)^\s*(?:[-*+]\s+|\d+[.)]\s+)(.+)$",
        answer,
    )
    normalized_points = [normalize_text(item) for item in markdown_contents]
    if len(normalized_points) != len(set(normalized_points)):
        issues.append("Danh sách còn lặp lại cùng một ý.")
    covered_points = markdown_points or len(sentences)
    if covered_points < target:
        issues.append(
            f"Cần bao phủ ít nhất {target} ý riêng biệt từ bằng chứng đã tìm thấy."
        )
    return issues


def answer_is_complete(
    answer: str,
    answer_profile: str,
    answer_depth: str = "STANDARD",
    evidence_count: int | None = None,
) -> bool:
    """Reject truncated or under-covered grounded answers."""
    return not answer_completeness_issues(
        answer,
        answer_profile,
        answer_depth,
        evidence_count,
    )


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


def list_item_label_supported(
    sentence: str,
    contexts: Sequence[RetrievedChunk],
) -> bool:
    """Reject a named list item when its label never appears in the evidence."""
    plain = re.sub(r"^\s*(?:[-*+]\s+|\d+[.)]\s+)", "", sentence).strip()
    plain = plain.replace("**", "")
    if ":" not in plain:
        return True

    label = plain.split(":", 1)[0].strip(" .,-")
    label_terms = content_terms(label)
    if len(label_terms) < 2:
        return True

    normalized_label = normalize_text(label)
    normalized_evidence = normalize_text(
        " ".join(context.content for context in contexts)
    )
    return normalized_label in normalized_evidence


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
    parts = re.split(r"(?<=[.!?])\s+|\n+", cleaned)
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
