from __future__ import annotations

import json
import math
import sqlite3
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .text_utils import tokenize


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


@dataclass(frozen=True)
class RetrievedChunk:
    chunk_id: str
    document_id: str
    filename: str
    subject: str
    chapter: str
    page: int | None
    content: str
    score: float
    semantic_score: float = 0.0
    lexical_score: float = 0.0


class SQLiteStore:
    def __init__(self, db_path: Path) -> None:
        self.db_path = db_path
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self.init_schema()

    def connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.db_path)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute("PRAGMA journal_mode = MEMORY")
        connection.execute("PRAGMA temp_store = MEMORY")
        return connection

    def init_schema(self) -> None:
        with self.connect() as connection:
            connection.executescript(
                """
                CREATE TABLE IF NOT EXISTS documents (
                    id TEXT PRIMARY KEY,
                    filename TEXT NOT NULL,
                    original_path TEXT NOT NULL,
                    subject TEXT NOT NULL,
                    chapter TEXT NOT NULL,
                    file_type TEXT NOT NULL,
                    file_hash TEXT NOT NULL,
                    uploaded_at TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS chunks (
                    id TEXT PRIMARY KEY,
                    document_id TEXT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
                    content TEXT NOT NULL,
                    page INTEGER,
                    chunk_index INTEGER NOT NULL,
                    embedding_model TEXT NOT NULL,
                    embedding_json TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS chat_sessions (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS messages (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    sources_json TEXT NOT NULL DEFAULT '[]',
                    created_at TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS benchmark_runs (
                    id TEXT PRIMARY KEY,
                    test_set_path TEXT NOT NULL,
                    embedding_model TEXT NOT NULL,
                    top_k INTEGER NOT NULL,
                    total_questions INTEGER NOT NULL,
                    metrics_json TEXT NOT NULL,
                    results_json TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );

                CREATE INDEX IF NOT EXISTS idx_chunks_model ON chunks(embedding_model);
                CREATE INDEX IF NOT EXISTS idx_chunks_document ON chunks(document_id);
                CREATE INDEX IF NOT EXISTS idx_messages_session ON messages(session_id, created_at);
                CREATE INDEX IF NOT EXISTS idx_benchmark_runs_created ON benchmark_runs(created_at);
                """
            )

    def add_document(
        self,
        filename: str,
        original_path: Path,
        subject: str,
        chapter: str,
        file_hash: str,
    ) -> str:
        document_id = str(uuid.uuid4())
        with self.connect() as connection:
            connection.execute(
                """
                INSERT INTO documents
                (id, filename, original_path, subject, chapter, file_type, file_hash, uploaded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    document_id,
                    filename,
                    str(original_path),
                    subject.strip() or "Chưa phân loại",
                    chapter.strip() or "Chưa phân chương",
                    original_path.suffix.lower().lstrip("."),
                    file_hash,
                    utc_now(),
                ),
            )
        return document_id

    def find_document_by_hash(self, file_hash: str) -> str | None:
        with self.connect() as connection:
            row = connection.execute(
                "SELECT id FROM documents WHERE file_hash = ? ORDER BY uploaded_at LIMIT 1",
                (file_hash,),
            ).fetchone()
        return row["id"] if row else None

    def delete_document_chunks(self, document_id: str, embedding_model: str) -> None:
        with self.connect() as connection:
            connection.execute(
                "DELETE FROM chunks WHERE document_id = ? AND embedding_model = ?",
                (document_id, embedding_model),
            )

    def add_chunks(
        self,
        document_id: str,
        chunks: list[Any],
        embeddings: list[list[float]],
        embedding_model: str,
    ) -> int:
        rows = []
        now = utc_now()
        for chunk, embedding in zip(chunks, embeddings):
            rows.append(
                (
                    str(uuid.uuid4()),
                    document_id,
                    chunk.text,
                    chunk.page,
                    chunk.chunk_index,
                    embedding_model,
                    json.dumps(embedding),
                    now,
                )
            )
        with self.connect() as connection:
            connection.executemany(
                """
                INSERT INTO chunks
                (id, document_id, content, page, chunk_index, embedding_model, embedding_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                rows,
            )
        return len(rows)

    def list_documents(self) -> list[dict[str, Any]]:
        with self.connect() as connection:
            rows = connection.execute(
                """
                SELECT d.*,
                       COUNT(c.id) AS num_chunks
                FROM documents d
                LEFT JOIN chunks c ON c.document_id = d.id
                GROUP BY d.id
                ORDER BY d.uploaded_at DESC
                """
            ).fetchall()
        return [dict(row) for row in rows]

    def list_subjects(self) -> list[str]:
        with self.connect() as connection:
            rows = connection.execute(
                "SELECT DISTINCT subject FROM documents ORDER BY subject"
            ).fetchall()
        return [row["subject"] for row in rows]

    def delete_document(self, document_id: str) -> None:
        with self.connect() as connection:
            connection.execute("DELETE FROM documents WHERE id = ?", (document_id,))

    def search_chunks(
        self,
        query_embedding: list[float],
        embedding_model: str,
        top_k: int,
        query_text: str = "",
        subject: str | None = None,
        semantic_weight: float = 0.65,
    ) -> list[RetrievedChunk]:
        semantic_weight = min(1.0, max(0.0, semantic_weight))
        lexical_weight = 1.0 - semantic_weight
        sql = """
            SELECT c.id AS chunk_id, c.document_id, c.content, c.page, c.embedding_json,
                   d.filename, d.subject, d.chapter
            FROM chunks c
            JOIN documents d ON d.id = c.document_id
            WHERE c.embedding_model = ?
        """
        params: list[Any] = [embedding_model]
        if subject:
            sql += " AND d.subject = ?"
            params.append(subject)
        with self.connect() as connection:
            rows = connection.execute(sql, params).fetchall()

        scored: list[RetrievedChunk] = []
        for row in rows:
            embedding = json.loads(row["embedding_json"])
            semantic_score = max(0.0, cosine_similarity(query_embedding, embedding))
            lexical_score = lexical_similarity(query_text, row["content"])
            score = (semantic_weight * semantic_score) + (lexical_weight * lexical_score)
            scored.append(
                RetrievedChunk(
                    chunk_id=row["chunk_id"],
                    document_id=row["document_id"],
                    filename=row["filename"],
                    subject=row["subject"],
                    chapter=row["chapter"],
                    page=row["page"],
                    content=row["content"],
                    score=score,
                    semantic_score=semantic_score,
                    lexical_score=lexical_score,
                )
            )
        scored.sort(key=lambda item: item.score, reverse=True)
        return scored[:top_k]

    def create_session(self, title: str = "Phiên chat mới") -> str:
        session_id = str(uuid.uuid4())
        with self.connect() as connection:
            connection.execute(
                "INSERT INTO chat_sessions (id, title, created_at) VALUES (?, ?, ?)",
                (session_id, title, utc_now()),
            )
        return session_id

    def list_sessions(self) -> list[dict[str, Any]]:
        with self.connect() as connection:
            rows = connection.execute(
                "SELECT * FROM chat_sessions ORDER BY created_at DESC"
            ).fetchall()
        return [dict(row) for row in rows]

    def add_message(
        self,
        session_id: str,
        role: str,
        content: str,
        sources: list[dict[str, Any]] | None = None,
    ) -> str:
        message_id = str(uuid.uuid4())
        with self.connect() as connection:
            connection.execute(
                """
                INSERT INTO messages (id, session_id, role, content, sources_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (
                    message_id,
                    session_id,
                    role,
                    content,
                    json.dumps(sources or [], ensure_ascii=False),
                    utc_now(),
                ),
            )
        return message_id

    def list_messages(self, session_id: str) -> list[dict[str, Any]]:
        with self.connect() as connection:
            rows = connection.execute(
                """
                SELECT * FROM messages
                WHERE session_id = ?
                ORDER BY created_at ASC
                """,
                (session_id,),
            ).fetchall()
        messages = []
        for row in rows:
            item = dict(row)
            item["sources"] = json.loads(item.pop("sources_json") or "[]")
            messages.append(item)
        return messages

    def add_benchmark_run(
        self,
        test_set_path: str,
        embedding_model: str,
        top_k: int,
        metrics: dict[str, Any],
        results: list[dict[str, Any]],
    ) -> str:
        run_id = str(uuid.uuid4())
        with self.connect() as connection:
            connection.execute(
                """
                INSERT INTO benchmark_runs
                (id, test_set_path, embedding_model, top_k, total_questions,
                 metrics_json, results_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    run_id,
                    test_set_path,
                    embedding_model,
                    top_k,
                    len(results),
                    json.dumps(metrics, ensure_ascii=False),
                    json.dumps(results, ensure_ascii=False),
                    utc_now(),
                ),
            )
        return run_id

    def list_benchmark_runs(self) -> list[dict[str, Any]]:
        with self.connect() as connection:
            rows = connection.execute(
                """
                SELECT id, test_set_path, embedding_model, top_k,
                       total_questions, metrics_json, created_at
                FROM benchmark_runs
                ORDER BY created_at DESC
                """
            ).fetchall()
        output = []
        for row in rows:
            item = dict(row)
            item["metrics"] = json.loads(item.pop("metrics_json"))
            output.append(item)
        return output

    def get_benchmark_run(self, run_id: str) -> dict[str, Any] | None:
        with self.connect() as connection:
            row = connection.execute(
                "SELECT * FROM benchmark_runs WHERE id = ?",
                (run_id,),
            ).fetchone()
        if not row:
            return None
        item = dict(row)
        item["metrics"] = json.loads(item.pop("metrics_json"))
        item["results"] = json.loads(item.pop("results_json"))
        return item


def cosine_similarity(left: list[float], right: list[float]) -> float:
    if not left or not right or len(left) != len(right):
        return 0.0
    numerator = sum(a * b for a, b in zip(left, right))
    left_norm = math.sqrt(sum(a * a for a in left))
    right_norm = math.sqrt(sum(b * b for b in right))
    if not left_norm or not right_norm:
        return 0.0
    return numerator / (left_norm * right_norm)


def lexical_similarity(query: str, content: str) -> float:
    query_tokens = meaningful_tokens(query)
    content_tokens = meaningful_tokens(content)
    if not query_tokens or not content_tokens:
        return 0.0
    overlap = len(query_tokens & content_tokens) / len(query_tokens)
    phrase_bonus = 0.15 if query.strip().lower() in content.lower() else 0.0
    return min(1.0, overlap + phrase_bonus)


def meaningful_tokens(text: str) -> set[str]:
    stop_words = {
        "là", "gì", "và", "có", "được", "như", "thế", "nào", "trong", "theo",
        "những", "các", "của", "về", "tại", "để", "một", "cho", "khi", "từ",
        "how", "what", "which", "the", "a", "an", "is", "are", "of", "to", "in",
    }
    return {token for token in tokenize(text) if len(token) > 1 and token not in stop_words}
