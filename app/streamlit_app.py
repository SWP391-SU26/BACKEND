from __future__ import annotations

import shutil
import sys
from datetime import datetime
from pathlib import Path

import streamlit as st

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from src.config import ensure_data_dirs, load_settings
from src.document_loader import SUPPORTED_EXTENSIONS
from src.embeddings import get_embedding_provider
from src.rag_pipeline import RAGPipeline
from src.storage import SQLiteStore
from src.text_utils import safe_filename


st.set_page_config(page_title="Chatbot RAG môn học", page_icon="💬", layout="wide")


@st.cache_resource(show_spinner=False)
def build_pipeline() -> tuple[RAGPipeline, SQLiteStore, object]:
    settings = load_settings()
    ensure_data_dirs(settings)
    store = SQLiteStore(settings.db_path)
    embedding_provider = get_embedding_provider(settings)
    pipeline = RAGPipeline(settings, store, embedding_provider)
    return pipeline, store, embedding_provider


def save_uploaded_file(uploaded_file) -> Path:
    settings = load_settings()
    ensure_data_dirs(settings)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"{timestamp}_{safe_filename(uploaded_file.name)}"
    destination = settings.raw_dir / filename
    with destination.open("wb") as handle:
        shutil.copyfileobj(uploaded_file, handle)
    return destination


def render_sources(sources: list[dict]) -> None:
    if not sources:
        return
    with st.expander("Nguồn tham chiếu", expanded=False):
        for source in sources:
            st.markdown(
                f"- **{source['filename']}**, {source['location']} "
                f"(score: `{source['score']}`)"
            )
            st.caption(source.get("preview", ""))


pipeline, store, embedding_provider = build_pipeline()
settings = load_settings()

with st.sidebar:
    st.title("Chatbot RAG")
    st.caption(f"Embedding: `{embedding_provider.model}`")
    st.caption(
        f"Top-k: `{settings.top_k}` | Chunk: `{settings.chunk_size}` | "
        f"Semantic: `{settings.semantic_weight}`"
    )

    sessions = store.list_sessions()
    session_options = {session["title"]: session["id"] for session in sessions}
    if "session_id" not in st.session_state:
        st.session_state.session_id = sessions[0]["id"] if sessions else store.create_session()

    if st.button("Tạo phiên chat mới", use_container_width=True):
        st.session_state.session_id = store.create_session(
            title=f"Phiên chat {datetime.now().strftime('%H:%M')}"
        )
        st.rerun()

    if session_options:
        current_title = next(
            (title for title, sid in session_options.items() if sid == st.session_state.session_id),
            None,
        )
        selected_title = st.selectbox(
            "Phiên chat",
            options=list(session_options.keys()),
            index=list(session_options.keys()).index(current_title) if current_title else 0,
        )
        st.session_state.session_id = session_options[selected_title]


tab_chat, tab_documents = st.tabs(["Chat", "Tài liệu"])

with tab_chat:
    st.header("Hỏi đáp tài liệu môn học")
    st.caption("Chatbot chỉ trả lời dựa trên tài liệu đã upload và index.")

    documents = store.list_documents()
    if not documents:
        st.info("Bạn cần upload và index ít nhất một tài liệu ở tab Tài liệu trước khi chat.")

    subject_options = ["Tất cả môn học", *store.list_subjects()]
    selected_subject = st.selectbox("Phạm vi môn học", subject_options)
    active_subject = None if selected_subject == "Tất cả môn học" else selected_subject

    messages = store.list_messages(st.session_state.session_id)
    for message in messages:
        with st.chat_message(message["role"]):
            st.markdown(message["content"])
            if message["role"] == "assistant":
                render_sources(message.get("sources", []))

    prompt = st.chat_input("Nhập câu hỏi của sinh viên...")
    if prompt:
        with st.chat_message("user"):
            st.markdown(prompt)
        with st.chat_message("assistant"):
            with st.spinner("Đang tìm trong tài liệu và tạo câu trả lời..."):
                result = pipeline.answer(
                    st.session_state.session_id,
                    prompt,
                    subject=active_subject,
                )
            st.markdown(result.answer)
            render_sources(result.sources)

with tab_documents:
    st.header("Quản lý tài liệu")
    st.caption("Upload PDF, DOCX, PPTX, TXT hoặc Markdown để tạo chỉ mục hỏi đáp.")

    with st.form("upload_form", clear_on_submit=True):
        subject = st.text_input("Môn học", value="Môn học demo")
        chapter = st.text_input("Chương / chủ đề", value="Chung")
        uploaded_files = st.file_uploader(
            "Chọn tài liệu",
            type=[item.lstrip(".") for item in sorted(SUPPORTED_EXTENSIONS)],
            accept_multiple_files=True,
        )
        submitted = st.form_submit_button("Upload và index")

    if submitted and uploaded_files:
        for uploaded_file in uploaded_files:
            with st.spinner(f"Đang xử lý {uploaded_file.name}..."):
                try:
                    path = save_uploaded_file(uploaded_file)
                    result = pipeline.ingest_file(path, subject=subject, chapter=chapter)
                    st.success(
                        f"Đã index {result.filename}: {result.num_pages} trang/slide, "
                        f"{result.num_chunks} chunks."
                    )
                except Exception as exc:
                    st.error(f"Lỗi khi xử lý {uploaded_file.name}: {exc}")
        st.cache_resource.clear()

    st.subheader("Tài liệu đã index")
    documents = store.list_documents()
    if not documents:
        st.info("Chưa có tài liệu nào.")
    else:
        for document in documents:
            cols = st.columns([3, 2, 2, 1, 1])
            cols[0].markdown(f"**{document['filename']}**")
            cols[1].write(document["subject"])
            cols[2].write(document["chapter"])
            cols[3].write(f"{document['num_chunks']} chunks")
            if cols[4].button("Xóa", key=f"delete_{document['id']}"):
                store.delete_document(document["id"])
                st.rerun()
