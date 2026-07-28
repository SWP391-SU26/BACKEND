from __future__ import annotations

import argparse
import csv
import hashlib
import json
import random
import re
import sys
from collections import defaultdict
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

from pypdf import PdfReader


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from src.grounded_answer import (
    clean_ocr_text,
    lowercase_first,
    normalize_text,
    split_sentences,
)


SOURCE_NAME = "75770b9b-cdbf-4038-90e2-f25e1f4426fe_triethocmaclenin.pdf"
SUBJECT = "Triết học Mác - Lênin"
SEED = 42
REVIEWED_TEST_LEAKAGE_QUESTIONS = {
    "Vấn đề cơ bản của triết học gồm mấy mặt?",
    "Quy luật phủ định của phủ định nêu khuynh hướng phát triển như thế nào?",
    "Thực chất cuộc cách mạng trong triết học do Mác và Ăngghen thực hiện là gì?",
    "Sự ra đời của triết học Mác gắn với điều kiện kinh tế - xã hội nào?",
    "Nguyên nhân kinh tế trực tiếp làm xuất hiện giai cấp là gì?",
    "Thực tiễn có vai trò gì đối với nhận thức?",
    "Chân lý là gì?",
}

CHAPTERS = [
    (3, "I", "Khái lược về Triết học"),
    (13, "II", "Khái lược về lịch sử triết học trước Mác"),
    (48, "III", "Sự ra đời và phát triển của triết học Mác - Lênin"),
    (66, "IV", "Một số trào lưu triết học phương Tây hiện đại"),
    (77, "V", "Vật chất và ý thức"),
    (95, "VI", "Hai nguyên lý của phép biện chứng duy vật"),
    (101, "VII", "Những cặp phạm trù cơ bản của phép biện chứng duy vật"),
    (121, "VIII", "Những quy luật cơ bản của phép biện chứng duy vật"),
    (136, "IX", "Lý luận nhận thức"),
    (148, "X", "Hình thái kinh tế - xã hội"),
    (163, "XI", "Giai cấp và dân tộc"),
    (174, "XII", "Nhà nước và cách mạng xã hội"),
    (186, "XIII", "Ý thức xã hội"),
    (202, "XIV", "Quan điểm triết học Mác - Lênin về con người"),
]

OUT_OF_SCOPE_QUESTIONS = [
    "Định luật Ohm phát biểu như thế nào?",
    "Công thức tính diện tích hình tròn là gì?",
    "Thuật toán Dijkstra tìm đường đi ngắn nhất ra sao?",
    "Ownership trong Rust hoạt động như thế nào?",
    "Quang hợp ở thực vật diễn ra qua những giai đoạn nào?",
    "Mô hình OSI có những tầng nào?",
    "Backpropagation cập nhật trọng số mạng nơ-ron ra sao?",
    "Hệ điều hành quản lý bộ nhớ ảo như thế nào?",
    "Blockchain đạt đồng thuận bằng những cơ chế nào?",
    "HTTP/2 khác HTTP/1.1 ở điểm nào?",
    "Phản ứng oxi hóa khử là gì?",
    "Định luật bảo toàn động lượng được phát biểu ra sao?",
    "Cấu trúc dữ liệu cây AVL cân bằng như thế nào?",
    "Docker container khác máy ảo ở điểm nào?",
    "SQL injection là gì và phòng tránh ra sao?",
    "REST API sử dụng các phương thức HTTP nào?",
    "Gradient descent tối ưu hàm mất mát như thế nào?",
    "Mạng 5G khác mạng 4G ở điểm nào?",
    "Hệ Mặt Trời có bao nhiêu hành tinh?",
    "Chiến thắng Điện Biên Phủ diễn ra năm nào?",
    "Cơ chế phiên mã DNA thành RNA diễn ra như thế nào?",
    "Điện toán đám mây có những mô hình dịch vụ nào?",
    "React reconciliation hoạt động ra sao?",
    "Kubernetes scheduler phân phối pod như thế nào?",
    "AES mã hóa dữ liệu theo nguyên lý nào?",
    "Tỷ giá ngoại tệ hôm nay là bao nhiêu?",
    "Dự báo thời tiết ngày mai như thế nào?",
    "Đội nào vô địch World Cup gần nhất?",
    "Giá cổ phiếu hiện tại của một công ty được xác định ra sao?",
    "Máy ảnh điều chỉnh khẩu độ để làm gì?",
    "Protein được tổng hợp trong tế bào như thế nào?",
    "Nguyên lý hoạt động của động cơ đốt trong là gì?",
    "Luật việt vị trong bóng đá được áp dụng như thế nào?",
    "Mã hóa khóa công khai RSA hoạt động ra sao?",
    "Hàm băm SHA-256 có đặc điểm gì?",
    "Cơ sở dữ liệu NoSQL khác SQL như thế nào?",
    "Wi-Fi 6 cải thiện hiệu năng bằng cách nào?",
    "Tế bào nhân sơ khác tế bào nhân thực ra sao?",
    "Công thức tính lãi suất kép là gì?",
    "Quy trình kiểm thử phần mềm gồm những giai đoạn nào?",
]

SUPPLEMENTAL_SEEDS = [
    {
        "question": "Những hình thức cộng đồng người nào xuất hiện trước dân tộc?",
        "expected_answer": "Trước khi dân tộc ra đời, các hình thức cộng đồng người phát triển từ thị tộc đến bộ lạc và bộ tộc.",
        "expected_page": "163",
        "category": "listing",
    },
    {
        "question": "Thị tộc là hình thức cộng đồng người như thế nào?",
        "expected_answer": "Thị tộc là cộng đồng gồm khoảng vài trăm người có cùng huyết thống, đồng thời là đơn vị sản xuất và hình thức tồn tại cơ bản của xã hội nguyên thủy.",
        "expected_page": "163",
        "category": "definition",
    },
    {
        "question": "Nguyên nhân kinh tế trực tiếp làm xuất hiện giai cấp là gì?",
        "expected_answer": "Sự phát triển của lực lượng sản xuất tạo ra của cải dư thừa, chế độ tư hữu và bất bình đẳng kinh tế; sự xuất hiện chế độ tư hữu là nguyên nhân quyết định trực tiếp cho sự ra đời của giai cấp.",
        "expected_page": "168",
        "category": "cause",
    },
    {
        "question": "Nguyên nhân trực tiếp dẫn đến sự xuất hiện của nhà nước là gì?",
        "expected_answer": "Nguyên nhân trực tiếp của sự xuất hiện nhà nước là những mâu thuẫn giai cấp không thể điều hòa được.",
        "expected_page": "174",
        "category": "cause",
    },
    {
        "question": "Theo giáo trình, bản chất của nhà nước là gì?",
        "expected_answer": "Nhà nước là bộ máy quyền lực do giai cấp thống trị về kinh tế thiết lập để duy trì sự thống trị và trấn áp các giai cấp khác.",
        "expected_page": "175",
        "category": "definition",
    },
    {
        "question": "Cách mạng xã hội theo nghĩa rộng và nghĩa hẹp được hiểu như thế nào?",
        "expected_answer": "Theo nghĩa rộng, cách mạng xã hội là biến đổi bước ngoặt và căn bản về chất trong mọi lĩnh vực đời sống; theo nghĩa hẹp, đó là việc lật đổ chế độ chính trị lỗi thời và thiết lập chế độ tiến bộ hơn.",
        "expected_page": "181",
        "category": "comparison",
    },
    {
        "question": "Nguyên nhân sâu xa của cách mạng xã hội là gì?",
        "expected_answer": "Nguyên nhân sâu xa của cách mạng xã hội là mâu thuẫn giữa lực lượng sản xuất đã phát triển với quan hệ sản xuất cũ trở nên lỗi thời và kìm hãm lực lượng sản xuất.",
        "expected_page": "182",
        "category": "cause",
    },
]

REFUSAL_ANSWER = (
    "Tôi chưa tìm thấy thông tin này trong tài liệu đã được huấn luyện."
)


@dataclass(frozen=True)
class ResearchRow:
    question: str
    expected_answer: str
    expected_source: str
    expected_page: str
    subject: str
    is_out_of_scope: bool
    category: str
    difficulty: str
    concept_id: str
    chapter: str
    evidence_quote: str


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--pdf",
        default=str(ROOT / "data" / "corpus" / "triethoc_mac_lenin.pdf"),
    )
    parser.add_argument(
        "--seed-csv",
        default=str(ROOT / "data" / "ground_truth_triethocmaclenin_100.csv"),
    )
    parser.add_argument(
        "--locked-test",
        default=str(
            ROOT.parent / "output" / "flow5" / "triethoc_mac_lenin_50_questions.csv"
        ),
    )
    parser.add_argument(
        "--output-dir",
        default=str(ROOT / "data" / "research" / "triethoc-v1"),
    )
    args = parser.parse_args()

    pdf_path = Path(args.pdf).resolve()
    seed_path = Path(args.seed_csv).resolve()
    test_path = Path(args.locked_test).resolve()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    reader = PdfReader(str(pdf_path))
    page_texts = [(page.extract_text() or "") for page in reader.pages]
    if len(page_texts) != 214:
        raise ValueError(f"Expected 214 PDF pages, found {len(page_texts)}.")

    locked_test = enrich_locked_test(load_csv(test_path), page_texts)
    curated_rows = enrich_seed_rows(load_csv(seed_path), page_texts)
    generated_rows = generate_pdf_seed_rows(page_texts, curated_rows, locked_test)
    train_rows, validation_rows = build_training_splits(
        curated_rows, generated_rows, locked_test
    )
    robustness_rows = build_out_of_scope_rows(10, offset=30, prefix="robustness")

    validate_splits(train_rows, validation_rows, locked_test, robustness_rows, page_texts)

    write_csv(output_dir / "train.csv", train_rows)
    write_csv(output_dir / "validation.csv", validation_rows)
    write_csv(output_dir / "test.csv", locked_test)
    write_csv(output_dir / "robustness.csv", robustness_rows)
    write_jsonl(output_dir / "train.jsonl", train_rows)
    write_jsonl(output_dir / "validation.jsonl", validation_rows)

    manifest = build_manifest(
        pdf_path,
        seed_path,
        test_path,
        output_dir,
        train_rows,
        validation_rows,
        locked_test,
        robustness_rows,
    )
    (output_dir / "dataset_manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


def load_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def enrich_locked_test(
    rows: list[dict[str, str]], page_texts: list[str]
) -> list[ResearchRow]:
    if len(rows) != 50:
        raise ValueError(f"Locked test set must contain 50 rows, found {len(rows)}.")
    result = []
    for index, row in enumerate(rows, start=1):
        page = required_page(row, page_texts)
        question = required(row, "question")
        answer = required(row, "expected_answer")
        result.append(
            ResearchRow(
                question=question,
                expected_answer=answer,
                expected_source=SOURCE_NAME,
                expected_page=str(page),
                subject=SUBJECT,
                is_out_of_scope=False,
                category=(row.get("category") or "FACTUAL").strip().lower(),
                difficulty=(row.get("difficulty") or "MEDIUM").strip().upper(),
                concept_id=concept_id(f"test:{index}:{question}"),
                chapter=chapter_for_page(page),
                evidence_quote=best_evidence(page_texts[page - 1], answer),
            )
        )
    return result


def enrich_seed_rows(
    rows: list[dict[str, str]], page_texts: list[str]
) -> list[ResearchRow]:
    result = []
    for index, row in enumerate(rows, start=1):
        if parse_bool(row.get("is_out_of_scope", "")):
            continue
        page = required_page(row, page_texts)
        question = required(row, "question")
        answer = required(row, "expected_answer")
        result.append(
            ResearchRow(
                question=question,
                expected_answer=answer,
                expected_source=SOURCE_NAME,
                expected_page=str(page),
                subject=SUBJECT,
                is_out_of_scope=False,
                category=(row.get("category") or "definition").strip().lower(),
                difficulty="MEDIUM",
                concept_id=concept_id(f"seed:{index}:{question}"),
                chapter=chapter_for_page(page),
                evidence_quote=best_evidence(page_texts[page - 1], answer),
            )
        )
    for index, row in enumerate(SUPPLEMENTAL_SEEDS, start=1):
        page = required_page(row, page_texts)
        question = required(row, "question")
        answer = required(row, "expected_answer")
        result.append(
            ResearchRow(
                question=question,
                expected_answer=answer,
                expected_source=SOURCE_NAME,
                expected_page=str(page),
                subject=SUBJECT,
                is_out_of_scope=False,
                category=(row.get("category") or "definition").strip().lower(),
                difficulty="MEDIUM",
                concept_id=concept_id(f"supplemental:{index}:{question}"),
                chapter=chapter_for_page(page),
                evidence_quote=best_evidence(page_texts[page - 1], answer),
            )
        )
    return result


def generate_pdf_seed_rows(
    page_texts: list[str],
    existing: list[ResearchRow],
    locked_test: list[ResearchRow],
) -> list[ResearchRow]:
    existing_questions = {normalize(row.question) for row in existing}
    existing_answers = {normalize(row.expected_answer) for row in existing}
    locked_questions = [normalize(row.question) for row in locked_test]
    locked_answers = {normalize(row.expected_answer) for row in locked_test}
    generated: list[ResearchRow] = []

    for page, page_text in enumerate(page_texts, start=1):
        for sentence_index, sentence in enumerate(split_sentences(page_text)):
            answer = clean_ocr_text(sentence).strip()
            if not 45 <= len(answer) <= 440:
                continue
            if not answer.endswith((".", "?", "!", ";")):
                continue
            if answer.count("(") != answer.count(")"):
                continue
            if starts_with_context_connector(answer):
                continue
            prompt = question_from_sentence(answer)
            if prompt is None:
                continue
            question, category = prompt
            normalized_question = normalize(question)
            normalized_answer = normalize(answer)
            if (
                normalized_question in existing_questions
                or normalized_answer in existing_answers
                or normalized_answer in locked_answers
                or near_locked_question(normalized_question, locked_questions)
            ):
                continue
            evidence = best_evidence(page_text, answer)
            if evidence_coverage(answer, evidence) < 0.65:
                continue
            generated.append(
                ResearchRow(
                    question=question,
                    expected_answer=answer,
                    expected_source=SOURCE_NAME,
                    expected_page=str(page),
                    subject=SUBJECT,
                    is_out_of_scope=False,
                    category=category,
                    difficulty="MEDIUM",
                    concept_id=concept_id(f"pdf:{page}:{sentence_index}:{question}"),
                    chapter=chapter_for_page(page),
                    evidence_quote=evidence,
                )
            )
            existing_questions.add(normalized_question)
            existing_answers.add(normalized_answer)
    return generated


def question_from_sentence(sentence: str) -> tuple[str, str] | None:
    patterns = (
        (r"\b(?:được gọi là|được hiểu là|là)\b", "definition", "là gì?"),
        (r"\b(?:bao gồm|gồm)\b", "listing", "gồm những nội dung nào?"),
    )
    for pattern, category, suffix in patterns:
        match = re.search(pattern, sentence, flags=re.IGNORECASE)
        if not match:
            continue
        subject = extract_subject(sentence[:match.start()])
        if not valid_subject(subject):
            continue
        return f"Theo giáo trình, {lowercase_first(subject)} {suffix}", category

    characteristic = re.search(
        r"\b(?:có|mang)\s+(?:những|các|một số)?\s*(?:đặc điểm|tính chất|vai trò)\b",
        sentence,
        flags=re.IGNORECASE,
    )
    if characteristic:
        subject = extract_subject(sentence[:characteristic.start()])
        if valid_subject(subject):
            return f"Theo giáo trình, {lowercase_first(subject)} có đặc điểm gì?", "factual"
    return None


def extract_subject(value: str) -> str:
    subject = re.split(r"[:;,.]", value)[-1]
    subject = re.sub(r"^[+\-–—\d.)\s]+", "", subject).strip()
    words = subject.split()
    if len(words) > 14:
        subject = " ".join(words[-14:])
    return subject.strip(" \"“”")


def valid_subject(subject: str) -> bool:
    normalized = normalize(subject)
    if not 2 <= len(normalized.split()) <= 14:
        return False
    if len(subject) < 6 or len(subject) > 110:
        return False
    rejected = (
        "đây ",
        "đó ",
        "nó ",
        "họ ",
        "ông ",
        "bà ",
        "chúng ",
        "điều này",
        "điều đó",
        "do đó",
        "từ đó",
        "như vậy",
        "hay ",
        "mà ",
        "và ",
        "nhưng ",
        "song ",
        "tuy nhiên",
        "thời kỳ thứ",
        "ví dụ",
        "thí dụ",
    )
    return not normalized.startswith(rejected)


def starts_with_context_connector(answer: str) -> bool:
    normalized = " ".join(normalize_text(answer).split())
    connectors = (
        "do do ",
        "tu do ",
        "nhu vay ",
        "vi vay ",
        "tuy nhien ",
        "song ",
        "nhung ",
        "va ",
        "hay ",
        "mat khac ",
        "dieu nay ",
        "dieu do ",
    )
    return normalized.startswith(connectors)


def near_locked_question(question: str, locked_questions: list[str]) -> bool:
    terms = set(question.split())
    for locked in locked_questions:
        locked_terms = set(locked.split())
        union = terms | locked_terms
        if union and len(terms & locked_terms) / len(union) >= 0.68:
            return True
    return False


def build_training_splits(
    curated_rows: list[ResearchRow],
    generated_rows: list[ResearchRow],
    locked_test: list[ResearchRow],
) -> tuple[list[ResearchRow], list[ResearchRow]]:
    test_questions = {normalize(row.question) for row in locked_test}
    test_question_list = list(test_questions)
    test_answers = {normalize(row.expected_answer) for row in locked_test}
    reviewed_leakage = {normalize(question) for question in REVIEWED_TEST_LEAKAGE_QUESTIONS}

    def is_eligible(row: ResearchRow) -> bool:
        return (
            normalize(row.question) not in test_questions
            and not near_locked_question(normalize(row.question), test_question_list)
            and normalize(row.question) not in reviewed_leakage
            and normalize(row.expected_answer) not in test_answers
            and not starts_with_context_connector(row.expected_answer)
            and row.expected_answer.strip().endswith((".", "?", "!", ";"))
            and row.expected_answer.count("(") == row.expected_answer.count(")")
            and evidence_coverage(row.expected_answer, row.evidence_quote) >= 0.40
        )

    curated = unique_concepts([
        row
        for row in curated_rows
        if is_eligible(row)
    ])
    generated = unique_concepts([
        row
        for row in generated_rows
        if is_eligible(row)
    ])
    if len(curated) < 75:
        raise ValueError(
            f"At least 75 curated non-leaking concepts are required, found {len(curated)}."
        )

    rng = random.Random(SEED)
    rng.shuffle(curated)
    validation_sources = stratified_take(curated, 45)
    validation_in_scope = [
        replace_question(
            source,
            source.question,
            f"validation:{source.concept_id}",
        )
        for source in validation_sources
    ]
    validation_question_list = [
        normalize(row.question) for row in validation_in_scope
    ]
    validation_source_ids = {row.concept_id for row in validation_sources}
    training_candidates: list[ResearchRow] = []
    for source in curated:
        if source.concept_id not in validation_source_ids:
            training_candidates.append(
                replace_question(
                    source,
                    source.question,
                    f"natural-sft:{source.concept_id}",
                )
            )
    for variant in range(4):
        for source in curated:
            training_candidates.append(training_variant(source, variant))

    # PDF-derived rows are only a fallback to preserve chapter coverage. The
    # supervised core stays on human-reviewed questions and clean answers.
    training_candidates.extend(generated)
    training_candidates = [
        row
        for row in training_candidates
        if normalize(row.question) not in test_questions
        and not near_locked_question(normalize(row.question), test_question_list)
        and not near_locked_question(
            normalize(row.question), validation_question_list
        )
        and normalize(row.expected_answer) not in test_answers
    ]
    train_in_scope = stratified_take(unique_question_answer_pairs(training_candidates), 225)

    train = train_in_scope + build_out_of_scope_rows(25, offset=0, prefix="train")
    validation = validation_in_scope + build_out_of_scope_rows(
        5, offset=25, prefix="validation"
    )
    rng.shuffle(train)
    rng.shuffle(validation)
    return train, validation


def unique_concepts(rows: list[ResearchRow]) -> list[ResearchRow]:
    result: list[ResearchRow] = []
    questions: set[str] = set()
    answers: set[str] = set()
    for row in rows:
        question = normalize(row.question)
        answer = normalize(row.expected_answer)
        if (
            question in questions
            or answer in answers
            or near_locked_question(question, list(questions))
        ):
            continue
        questions.add(question)
        answers.add(answer)
        result.append(row)
    return result


def unique_question_answer_pairs(rows: list[ResearchRow]) -> list[ResearchRow]:
    result: list[ResearchRow] = []
    pairs: set[tuple[str, str]] = set()
    for row in rows:
        pair = (normalize(row.question), normalize(row.expected_answer))
        if pair in pairs:
            continue
        pairs.add(pair)
        result.append(row)
    return result


def training_variant(source: ResearchRow, variant: int) -> ResearchRow:
    values = asdict(source)
    topic = question_topic(source.question)
    templates = (
        "Trình bày nội dung trọng tâm của giáo trình về {topic}.",
        "Hãy giải thích kiến thức Triết học Mác - Lênin liên quan đến {topic}.",
        "Tóm tắt luận điểm có các từ khóa sau: {topic}.",
        "Ôn tập mục kiến thức có các từ khóa: {topic}.",
    )
    display_topic = question_keywords(source.question) if variant == 3 else topic
    values["question"] = templates[variant % len(templates)].format(
        topic=display_topic
    )
    values["expected_answer"] = paraphrase_training_answer(
        source.expected_answer, variant
    )
    values["concept_id"] = concept_id(
        f"domain-sft:{source.concept_id}:{variant}:{values['question']}"
    )
    return ResearchRow(**values)


def question_topic(question: str) -> str:
    topic = re.sub(
        r"^(?:theo giáo trình,\s*|dựa trên giáo trình,\s*)",
        "",
        question.strip(),
        flags=re.IGNORECASE,
    )
    topic = re.sub(
        r"\b(?:là gì|gồm mấy mặt|gồm những gì|gồm những nội dung nào|"
        r"như thế nào|ra sao|vì sao|tại sao|hỏi điều gì)\??$",
        "",
        topic,
        flags=re.IGNORECASE,
    ).strip(" .,:;?\"")
    if not topic:
        topic = question.strip(" .,:;?")
    return lowercase_first(topic)


def question_keywords(question: str) -> str:
    ignored = {
        "có",
        "của",
        "được",
        "giáo",
        "gì",
        "hỏi",
        "là",
        "mấy",
        "nào",
        "như",
        "phần",
        "ra",
        "sao",
        "thế",
        "theo",
        "thế",
        "trình",
        "vì",
    }
    words = re.findall(r"[^\W_]+", question.casefold(), flags=re.UNICODE)
    selected = [word for word in words if word not in ignored][:6]
    return " ".join(selected) or question_topic(question)


def paraphrase_training_answer(answer: str, variant: int) -> str:
    clean = answer.strip()
    replacements = (
        (r"\blà\b", "được hiểu là"),
        (r"\bgồm\b", "bao gồm"),
        (r"\bdo\b", "bắt nguồn từ"),
        (r"\bcó vai trò\b", "giữ vai trò"),
        (r"\bcho rằng\b", "khẳng định rằng"),
    )
    pattern, replacement = replacements[variant % len(replacements)]
    rewritten, count = re.subn(
        pattern, replacement, clean, count=1, flags=re.IGNORECASE
    )
    if count == 0:
        words = clean.split()
        insert_at = min(4, max(1, len(words) - 1))
        words[insert_at:insert_at] = ["theo", "cách", "trình", "bày", "của", "tài", "liệu"]
        rewritten = " ".join(words)
    return rewritten


def stratified_take(rows: list[ResearchRow], count: int) -> list[ResearchRow]:
    groups: dict[str, list[ResearchRow]] = defaultdict(list)
    for row in rows:
        groups[row.chapter].append(row)

    selected: list[ResearchRow] = []
    selected_ids: set[str] = set()
    chapter_names = [f"Chương {roman}" for _page, roman, _title in CHAPTERS]
    while len(selected) < count:
        made_progress = False
        for chapter in chapter_names:
            group = groups.get(chapter) or []
            while group and group[0].concept_id in selected_ids:
                group.pop(0)
            if not group:
                continue
            row = group.pop(0)
            selected.append(row)
            selected_ids.add(row.concept_id)
            made_progress = True
            if len(selected) == count:
                break
        if not made_progress:
            break
    if len(selected) != count:
        raise ValueError(f"Could only select {len(selected)} of {count} stratified concepts.")
    return selected


def replace_question(source: ResearchRow, question: str, key: str) -> ResearchRow:
    values = asdict(source)
    values["question"] = question
    values["concept_id"] = concept_id(key)
    return ResearchRow(**values)


def paraphrase_question(question: str, variant: int) -> str:
    clean = question.strip()
    templates = [
        "Dựa trên giáo trình, hãy trả lời câu hỏi sau: {question}",
        "Theo nội dung môn học, {lower_question}",
        "Không sử dụng kiến thức ngoài tài liệu, hãy giải đáp: {question}",
        "Từ giáo trình Triết học Mác - Lênin, hãy làm rõ: {question}",
    ]
    template = templates[variant % len(templates)]
    return template.format(
        question=clean,
        lower_question=clean[:1].lower() + clean[1:],
    )


def build_out_of_scope_rows(
    count: int, offset: int, prefix: str
) -> list[ResearchRow]:
    questions = OUT_OF_SCOPE_QUESTIONS[offset : offset + count]
    if len(questions) != count:
        raise ValueError("Not enough unique out-of-scope questions.")
    return [
        ResearchRow(
            question=question,
            expected_answer=REFUSAL_ANSWER,
            expected_source="",
            expected_page="",
            subject=SUBJECT,
            is_out_of_scope=True,
            category="out_of_scope",
            difficulty="MEDIUM",
            concept_id=concept_id(f"{prefix}:oos:{offset + index}:{question}"),
            chapter="OUT_OF_SCOPE",
            evidence_quote="",
        )
        for index, question in enumerate(questions)
    ]


def validate_splits(
    train: list[ResearchRow],
    validation: list[ResearchRow],
    test: list[ResearchRow],
    robustness: list[ResearchRow],
    page_texts: list[str],
) -> None:
    expected_sizes = {
        "train": (train, 250),
        "validation": (validation, 50),
        "test": (test, 50),
        "robustness": (robustness, 10),
    }
    for name, (rows, size) in expected_sizes.items():
        if len(rows) != size:
            raise ValueError(f"{name} must contain {size} rows, found {len(rows)}.")
        normalized = [normalize(row.question) for row in rows]
        if len(normalized) != len(set(normalized)):
            raise ValueError(f"{name} contains duplicate questions.")

    train_questions = {normalize(row.question) for row in train}
    train_answers = {normalize(row.expected_answer) for row in train if not row.is_out_of_scope}
    validation_questions = {normalize(row.question) for row in validation}
    test_questions = {normalize(row.question) for row in test}
    test_answers = {normalize(row.expected_answer) for row in test}
    if train_questions & test_questions or validation_questions & test_questions:
        raise ValueError("Locked test questions leaked into train/validation.")
    if train_answers & test_answers:
        raise ValueError("Locked test answers leaked verbatim into train.")
    validation_answers = {
        normalize(row.expected_answer) for row in validation if not row.is_out_of_scope
    }
    if train_answers & validation_answers:
        raise ValueError("Training answers leaked verbatim into validation.")

    covered_chapters = {
        row.chapter for row in train + validation if not row.is_out_of_scope
    }
    expected_chapters = {f"Chương {roman}" for _page, roman, _title in CHAPTERS}
    if not expected_chapters.issubset(covered_chapters):
        missing = sorted(expected_chapters - covered_chapters)
        raise ValueError(f"Dataset does not cover all chapters: {missing}")

    for row in train + validation + test:
        if row.is_out_of_scope:
            continue
        page = int(row.expected_page)
        if row.evidence_quote not in page_texts[page - 1]:
            raise ValueError(
                f"Evidence for concept {row.concept_id} is not present on page {page}."
            )
        minimum_coverage = 0.40 if row in train or row in validation else 0.25
        if evidence_coverage(row.expected_answer, row.evidence_quote) < minimum_coverage:
            raise ValueError(
                f"Evidence for concept {row.concept_id} does not support enough answer terms."
            )


def best_evidence(page_text: str, answer: str) -> str:
    paragraphs = [
        paragraph.strip()
        for paragraph in re.split(r"\n\s*\n", page_text)
        if len(paragraph.strip()) >= 40
    ]
    if not paragraphs:
        paragraphs = [page_text.strip()]
    answer_terms = set(tokens(answer))

    def score(paragraph: str) -> tuple[float, int]:
        paragraph_terms = set(tokens(paragraph))
        overlap = len(answer_terms & paragraph_terms) / max(1, len(answer_terms))
        return overlap, -abs(len(paragraph) - 400)

    selected = max(paragraphs, key=score)
    if len(selected) <= 1600:
        return selected
    candidates = [
        selected[start : start + 1600]
        for start in range(0, max(1, len(selected) - 1599), 250)
    ]
    tail = selected[-1600:]
    if tail not in candidates:
        candidates.append(tail)
    return max(
        candidates,
        key=lambda candidate: (
            evidence_coverage(answer, candidate),
            -abs(len(candidate) - 1200),
        ),
    )


def evidence_coverage(answer: str, evidence: str) -> float:
    answer_terms = set(tokens(answer))
    evidence_terms = set(tokens(evidence))
    return len(answer_terms & evidence_terms) / max(1, len(answer_terms))


def required_page(row: dict[str, str], page_texts: list[str]) -> int:
    raw = (row.get("expected_page") or "").strip()
    if not raw.isdigit():
        raise ValueError(f"Invalid expected_page: {raw!r}")
    page = int(raw)
    if page < 1 or page > len(page_texts):
        raise ValueError(f"Page {page} is outside the PDF.")
    return page


def required(row: dict[str, str], key: str) -> str:
    value = (row.get(key) or "").strip()
    if not value:
        raise ValueError(f"Missing required field {key}.")
    return value


def chapter_for_page(page: int) -> str:
    selected = CHAPTERS[0]
    for chapter in CHAPTERS:
        if page >= chapter[0]:
            selected = chapter
        else:
            break
    return f"Chương {selected[1]}"


def parse_bool(value: str) -> bool:
    return value.strip().casefold() in {"true", "1", "yes", "y", "có", "co"}


def normalize(value: str) -> str:
    return " ".join(tokens(value))


def tokens(value: str) -> list[str]:
    return re.findall(r"[^\W_]+", value.casefold(), flags=re.UNICODE)


def concept_id(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()[:20]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_csv(path: Path, rows: list[ResearchRow]) -> None:
    fields = list(asdict(rows[0]).keys())
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(asdict(row) for row in rows)


def write_jsonl(path: Path, rows: list[ResearchRow]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            allowed_sources = [] if row.is_out_of_scope else [row.expected_source]
            item: dict[str, Any] = {
                "messages": [
                    {
                        "role": "system",
                        "content": (
                            f"Bạn là trợ lý học tập đã được fine-tune cho {SUBJECT}. "
                            f"Chỉ trả lời kiến thức thuộc nguồn {SOURCE_NAME}. "
                            f"Nếu ngoài phạm vi, chỉ trả lời: {REFUSAL_ANSWER}"
                        ),
                    },
                    {"role": "user", "content": row.question},
                    {"role": "assistant", "content": row.expected_answer},
                ],
                "metadata": {
                    "source": row.expected_source,
                    "allowed_sources": allowed_sources or [SOURCE_NAME],
                    "page": row.expected_page,
                    "subject": row.subject,
                    "category": row.category,
                    "difficulty": row.difficulty,
                    "concept_id": row.concept_id,
                    "chapter": row.chapter,
                    "evidence_quote": row.evidence_quote,
                    "is_out_of_scope": row.is_out_of_scope,
                },
            }
            handle.write(json.dumps(item, ensure_ascii=False) + "\n")


def build_manifest(
    pdf_path: Path,
    seed_path: Path,
    locked_test_path: Path,
    output_dir: Path,
    train: list[ResearchRow],
    validation: list[ResearchRow],
    test: list[ResearchRow],
    robustness: list[ResearchRow],
) -> dict[str, Any]:
    files = {
        name: {
            "path": str((output_dir / name).relative_to(ROOT)),
            "sha256": sha256(output_dir / name),
        }
        for name in (
            "train.csv",
            "validation.csv",
            "test.csv",
            "robustness.csv",
            "train.jsonl",
            "validation.jsonl",
        )
    }
    return {
        "schema_version": 1,
        "dataset_version": "triethoc-v1",
        "seed": SEED,
        "subject": SUBJECT,
        "source": {
            "filename": SOURCE_NAME,
            "canonical_path": str(pdf_path.relative_to(ROOT)),
            "pages": 214,
            "sha256": sha256(pdf_path),
        },
        "inputs": {
            "seed_csv": str(seed_path.relative_to(ROOT)),
            "seed_csv_sha256": sha256(seed_path),
            "locked_test": str(locked_test_path),
            "locked_test_sha256": sha256(locked_test_path),
        },
        "counts": {
            "train": len(train),
            "train_in_scope": sum(not row.is_out_of_scope for row in train),
            "train_out_of_scope": sum(row.is_out_of_scope for row in train),
            "validation": len(validation),
            "validation_in_scope": sum(not row.is_out_of_scope for row in validation),
            "validation_out_of_scope": sum(row.is_out_of_scope for row in validation),
            "test": len(test),
            "robustness": len(robustness),
        },
        "chapters": [f"Chương {roman}" for _page, roman, _title in CHAPTERS],
        "leakage_policy": {
            "locked_test_questions_in_training": False,
            "locked_test_answers_verbatim_in_training": False,
            "train_validation_answers_verbatim_overlap": False,
            "semantic_near_duplicates_require_review": True,
            "reviewed_test_concepts_excluded": len(REVIEWED_TEST_LEAKAGE_QUESTIONS),
            "minimum_train_validation_evidence_coverage": 0.40,
            "minimum_test_evidence_coverage": 0.25,
        },
        "files": files,
    }


if __name__ == "__main__":
    main()
