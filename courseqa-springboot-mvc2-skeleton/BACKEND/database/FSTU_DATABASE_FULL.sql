/*
    FStu - Toan bo database trong MOT file duy nhat.

    SINH TU DONG: noi VietnameseCourseQA20DB.sql + toan bo V*.sql theo dung
    thu tu liet ke trong APPLY_ALL.sql. Khong co lenh :r nen KHONG can file
    kem theo, chay duoc bang SSMS thuong (khong can bat SQLCMD Mode).

    Tu tao database VietnameseCourseQA20DB neu chua co.

    !! CANH BAO: phan schema goc DROP 21 bang truoc khi tao lai.
    !! Chay tren database DANG CO DU LIEU se XOA SACH du lieu do.
    !! Chi dung de dung moi tu dau.

    Cach chay:
      - SSMS: mo file, chon server, bam Execute.
      - sqlcmd: sqlcmd -S localhost,1433 -U sa -P <mat_khau> -C -i FSTU_DATABASE_FULL.sql

    Sau khi them migration moi phai sinh lai file nay theo thu tu trong APPLY_ALL.sql.
*/

-- ============================================================
-- VietnameseCourseQA20DB.sql
-- ============================================================
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO
IF DB_ID(N'VietnameseCourseQA20DB') IS NULL CREATE DATABASE VietnameseCourseQA20DB;
GO
USE VietnameseCourseQA20DB;
GO
IF OBJECT_ID('v_experiment_dashboard','V') IS NOT NULL DROP VIEW v_experiment_dashboard;
IF OBJECT_ID('experiment_results','U') IS NOT NULL DROP TABLE experiment_results;
IF OBJECT_ID('experiments','U') IS NOT NULL DROP TABLE experiments;
IF OBJECT_ID('evaluation_questions','U') IS NOT NULL DROP TABLE evaluation_questions;
IF OBJECT_ID('evaluation_datasets','U') IS NOT NULL DROP TABLE evaluation_datasets;
IF OBJECT_ID('saved_notes','U') IS NOT NULL DROP TABLE saved_notes;
IF OBJECT_ID('answer_citations','U') IS NOT NULL DROP TABLE answer_citations;
IF OBJECT_ID('retrieval_results','U') IS NOT NULL DROP TABLE retrieval_results;
IF OBJECT_ID('retrieval_queries','U') IS NOT NULL DROP TABLE retrieval_queries;
IF OBJECT_ID('chat_messages','U') IS NOT NULL DROP TABLE chat_messages;
IF OBJECT_ID('chat_session_documents','U') IS NOT NULL DROP TABLE chat_session_documents;
IF OBJECT_ID('chat_sessions','U') IS NOT NULL DROP TABLE chat_sessions;
IF OBJECT_ID('chunk_embeddings','U') IS NOT NULL DROP TABLE chunk_embeddings;
IF OBJECT_ID('embedding_models','U') IS NOT NULL DROP TABLE embedding_models;
IF OBJECT_ID('document_chunks','U') IS NOT NULL DROP TABLE document_chunks;
IF OBJECT_ID('document_pages','U') IS NOT NULL DROP TABLE document_pages;
IF OBJECT_ID('course_documents','U') IS NOT NULL DROP TABLE course_documents;
IF OBJECT_ID('course_workspaces','U') IS NOT NULL DROP TABLE course_workspaces;
IF OBJECT_ID('chapters','U') IS NOT NULL DROP TABLE chapters;
IF OBJECT_ID('courses','U') IS NOT NULL DROP TABLE courses;
IF OBJECT_ID('user_roles','U') IS NOT NULL DROP TABLE user_roles;
IF OBJECT_ID('users','U') IS NOT NULL DROP TABLE users;
GO
CREATE TABLE users(user_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),full_name NVARCHAR(255) NOT NULL,email NVARCHAR(255) NOT NULL UNIQUE,password_hash NVARCHAR(MAX) NOT NULL,avatar_url NVARCHAR(MAX),is_active BIT NOT NULL DEFAULT 1,last_login_at DATETIME2,last_logout_at DATETIME2,created_at DATETIME2 NOT NULL DEFAULT GETDATE(),updated_at DATETIME2 NOT NULL DEFAULT GETDATE());
CREATE TABLE user_roles(user_role_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),user_id UNIQUEIDENTIFIER NOT NULL,role_name NVARCHAR(50) NOT NULL,permission_json NVARCHAR(MAX),assigned_at DATETIME2 NOT NULL DEFAULT GETDATE(),is_active BIT NOT NULL DEFAULT 1,CONSTRAINT fk_user_roles_user FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE CASCADE,CONSTRAINT chk_user_roles_role CHECK(role_name IN('ADMIN','TEACHER','STUDENT','RESEARCHER')));
CREATE TABLE courses(course_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),course_code NVARCHAR(50) NOT NULL UNIQUE,course_name NVARCHAR(255) NOT NULL,description NVARCHAR(MAX),created_by UNIQUEIDENTIFIER,is_active BIT NOT NULL DEFAULT 1,created_at DATETIME2 NOT NULL DEFAULT GETDATE(),updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),CONSTRAINT fk_courses_created_by FOREIGN KEY(created_by) REFERENCES users(user_id) ON DELETE SET NULL);
CREATE TABLE chapters(chapter_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),course_id UNIQUEIDENTIFIER NOT NULL,chapter_title NVARCHAR(255) NOT NULL,description NVARCHAR(MAX),order_index INT NOT NULL DEFAULT 1,is_active BIT NOT NULL DEFAULT 1,created_at DATETIME2 NOT NULL DEFAULT GETDATE(),updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),CONSTRAINT fk_chapters_course FOREIGN KEY(course_id) REFERENCES courses(course_id) ON DELETE CASCADE,CONSTRAINT uq_chapters_course_order UNIQUE(course_id,order_index));
CREATE TABLE course_workspaces(workspace_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),course_id UNIQUEIDENTIFIER NULL,owner_user_id UNIQUEIDENTIFIER,workspace_title NVARCHAR(255) NOT NULL,description NVARCHAR(MAX),visibility NVARCHAR(50) NOT NULL DEFAULT 'PRIVATE',is_active BIT NOT NULL DEFAULT 1,created_at DATETIME2 NOT NULL DEFAULT GETDATE(),updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),CONSTRAINT fk_workspaces_course FOREIGN KEY(course_id) REFERENCES courses(course_id) ON DELETE CASCADE,CONSTRAINT fk_workspaces_owner FOREIGN KEY(owner_user_id) REFERENCES users(user_id) ON DELETE SET NULL);
CREATE UNIQUE INDEX uq_personal_workspace_owner ON course_workspaces(owner_user_id) WHERE course_id IS NULL AND owner_user_id IS NOT NULL AND visibility='PRIVATE';
CREATE TABLE course_documents(document_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),workspace_id UNIQUEIDENTIFIER NOT NULL,course_id UNIQUEIDENTIFIER NULL,chapter_id UNIQUEIDENTIFIER,uploaded_by UNIQUEIDENTIFIER,document_title NVARCHAR(255) NOT NULL,original_filename NVARCHAR(255) NOT NULL,file_type NVARCHAR(20) NOT NULL,mime_type NVARCHAR(150),file_path NVARCHAR(MAX) NOT NULL,storage_provider NVARCHAR(50) DEFAULT 'LOCAL',cloudinary_public_id NVARCHAR(255),cloudinary_secure_url NVARCHAR(MAX),cloudinary_preview_public_id NVARCHAR(255),cloudinary_preview_url NVARCHAR(MAX),file_size_bytes BIGINT,processing_status NVARCHAR(50) NOT NULL DEFAULT 'UPLOADED',indexing_status NVARCHAR(30) DEFAULT 'PENDING',indexed_embedding_model_id UNIQUEIDENTIFIER,indexed_model_version NVARCHAR(255),indexed_at DATETIME2,index_error NVARCHAR(MAX),total_pages INT,language NVARCHAR(20) NOT NULL DEFAULT 'vi',error_message NVARCHAR(MAX),document_scope NVARCHAR(20) NOT NULL DEFAULT 'COURSE',review_status NVARCHAR(20) NOT NULL DEFAULT 'APPROVED',target_course_id UNIQUEIDENTIFIER,submitted_at DATETIME2,reviewed_by UNIQUEIDENTIFIER,reviewed_at DATETIME2,rejection_reason NVARCHAR(MAX),uploaded_at DATETIME2 NOT NULL DEFAULT GETDATE(),updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),CONSTRAINT chk_course_documents_scope CHECK(document_scope IN('PERSONAL','COURSE')),CONSTRAINT chk_course_documents_review_status CHECK(review_status IN('NOT_SUBMITTED','PENDING','APPROVED','REJECTED')),CONSTRAINT fk_docs_workspace FOREIGN KEY(workspace_id) REFERENCES course_workspaces(workspace_id) ON DELETE CASCADE,CONSTRAINT fk_docs_course FOREIGN KEY(course_id) REFERENCES courses(course_id) ON DELETE NO ACTION,CONSTRAINT fk_docs_target_course FOREIGN KEY(target_course_id) REFERENCES courses(course_id) ON DELETE NO ACTION,CONSTRAINT fk_docs_reviewed_by FOREIGN KEY(reviewed_by) REFERENCES users(user_id) ON DELETE NO ACTION,CONSTRAINT fk_docs_chapter FOREIGN KEY(chapter_id) REFERENCES chapters(chapter_id) ON DELETE NO ACTION,CONSTRAINT fk_docs_uploaded_by FOREIGN KEY(uploaded_by) REFERENCES users(user_id) ON DELETE SET NULL);
CREATE TABLE document_pages(page_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),document_id UNIQUEIDENTIFIER NOT NULL,page_number INT NOT NULL,raw_text NVARCHAR(MAX),cleaned_text NVARCHAR(MAX),word_count INT,char_count INT,extraction_status NVARCHAR(50) NOT NULL DEFAULT 'TEXT_EXTRACTED',error_message NVARCHAR(MAX),extracted_at DATETIME2 NOT NULL DEFAULT GETDATE(),CONSTRAINT fk_pages_doc FOREIGN KEY(document_id) REFERENCES course_documents(document_id) ON DELETE CASCADE,CONSTRAINT uq_pages_document_page UNIQUE(document_id,page_number));
CREATE TABLE document_chunks(chunk_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),document_id UNIQUEIDENTIFIER NOT NULL,workspace_id UNIQUEIDENTIFIER NOT NULL,course_id UNIQUEIDENTIFIER NULL,chapter_id UNIQUEIDENTIFIER,chunk_index INT NOT NULL,chunk_strategy NVARCHAR(100) NOT NULL DEFAULT 'fixed_1200_150',chunk_size INT,chunk_overlap INT,content NVARCHAR(MAX) NOT NULL,page_start INT,page_end INT,token_count INT,word_count INT,char_count INT,created_at DATETIME2 NOT NULL DEFAULT GETDATE(),CONSTRAINT fk_chunks_doc FOREIGN KEY(document_id) REFERENCES course_documents(document_id) ON DELETE CASCADE,CONSTRAINT fk_chunks_workspace FOREIGN KEY(workspace_id) REFERENCES course_workspaces(workspace_id) ON DELETE NO ACTION,CONSTRAINT fk_chunks_course FOREIGN KEY(course_id) REFERENCES courses(course_id) ON DELETE NO ACTION,CONSTRAINT fk_chunks_chapter FOREIGN KEY(chapter_id) REFERENCES chapters(chapter_id) ON DELETE NO ACTION);
CREATE TABLE embedding_models(embedding_model_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),model_name NVARCHAR(150) NOT NULL UNIQUE,provider NVARCHAR(100) NOT NULL,dimension INT NOT NULL,is_local BIT NOT NULL DEFAULT 0,description NVARCHAR(MAX),config_json NVARCHAR(MAX),is_active BIT NOT NULL DEFAULT 1,created_at DATETIME2 NOT NULL DEFAULT GETDATE());
CREATE TABLE chunk_embeddings(chunk_embedding_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),chunk_id UNIQUEIDENTIFIER NOT NULL,embedding_model_id UNIQUEIDENTIFIER NOT NULL,embedding_json NVARCHAR(MAX) NOT NULL,dimension INT NOT NULL,created_at DATETIME2 NOT NULL DEFAULT GETDATE(),CONSTRAINT fk_emb_chunk FOREIGN KEY(chunk_id) REFERENCES document_chunks(chunk_id) ON DELETE CASCADE,CONSTRAINT fk_emb_model FOREIGN KEY(embedding_model_id) REFERENCES embedding_models(embedding_model_id) ON DELETE NO ACTION);
CREATE TABLE chat_sessions(chat_session_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),workspace_id UNIQUEIDENTIFIER NULL,user_id UNIQUEIDENTIFIER,course_id UNIQUEIDENTIFIER NULL,semester_workspace_id UNIQUEIDENTIFIER NULL,scope_type NVARCHAR(20) NOT NULL DEFAULT 'COURSE',chapter_id UNIQUEIDENTIFIER,session_title NVARCHAR(255),selected_embedding_model_id UNIQUEIDENTIFIER,selected_chunking_strategy NVARCHAR(100),system_prompt NVARCHAR(MAX),is_active BIT NOT NULL DEFAULT 1,is_pinned BIT NOT NULL DEFAULT 0,pinned_at DATETIME2 NULL,started_at DATETIME2 NOT NULL DEFAULT GETDATE(),updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),CONSTRAINT chk_chat_scope_type CHECK(scope_type IN ('PERSONAL','DOCUMENTS','COURSE','SEMESTER')),CONSTRAINT fk_chat_workspace FOREIGN KEY(workspace_id) REFERENCES course_workspaces(workspace_id) ON DELETE CASCADE,CONSTRAINT fk_chat_user FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE SET NULL,CONSTRAINT fk_chat_course FOREIGN KEY(course_id) REFERENCES courses(course_id) ON DELETE NO ACTION,CONSTRAINT fk_chat_chapter FOREIGN KEY(chapter_id) REFERENCES chapters(chapter_id) ON DELETE NO ACTION);
CREATE TABLE chat_session_documents(chat_session_document_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),chat_session_id UNIQUEIDENTIFIER NOT NULL,document_id UNIQUEIDENTIFIER NOT NULL,CONSTRAINT uq_chat_session_document UNIQUE(chat_session_id,document_id),CONSTRAINT fk_chat_session_document_session FOREIGN KEY(chat_session_id) REFERENCES chat_sessions(chat_session_id) ON DELETE CASCADE,CONSTRAINT fk_chat_session_document_document FOREIGN KEY(document_id) REFERENCES course_documents(document_id) ON DELETE NO ACTION);
CREATE TABLE chat_messages(message_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),chat_session_id UNIQUEIDENTIFIER NOT NULL,sender_role NVARCHAR(50) NOT NULL,message_content NVARCHAR(MAX) NOT NULL,llm_model NVARCHAR(150),input_tokens INT,output_tokens INT,total_tokens INT,latency_ms INT,answer_depth NVARCHAR(20),question_intent NVARCHAR(40),processing_trace_json NVARCHAR(MAX),cost DECIMAL(18,6),created_at DATETIME2 NOT NULL DEFAULT GETDATE(),CONSTRAINT fk_msg_session FOREIGN KEY(chat_session_id) REFERENCES chat_sessions(chat_session_id) ON DELETE CASCADE);
CREATE TABLE retrieval_queries(retrieval_query_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),chat_session_id UNIQUEIDENTIFIER NOT NULL,user_message_id UNIQUEIDENTIFIER NOT NULL,workspace_id UNIQUEIDENTIFIER NULL,semester_workspace_id UNIQUEIDENTIFIER NULL,scope_type NVARCHAR(20) NOT NULL DEFAULT 'COURSE',query_text NVARCHAR(MAX) NOT NULL,rewritten_query NVARCHAR(MAX),embedding_model_id UNIQUEIDENTIFIER,top_k INT NOT NULL DEFAULT 5,similarity_metric NVARCHAR(50) NOT NULL DEFAULT 'keyword',similarity_threshold FLOAT NOT NULL DEFAULT 0.25,is_answerable BIT,no_answer_reason NVARCHAR(MAX),latency_ms INT,created_at DATETIME2 NOT NULL DEFAULT GETDATE(),CONSTRAINT fk_rq_session FOREIGN KEY(chat_session_id) REFERENCES chat_sessions(chat_session_id) ON DELETE CASCADE,CONSTRAINT fk_rq_msg FOREIGN KEY(user_message_id) REFERENCES chat_messages(message_id) ON DELETE NO ACTION,CONSTRAINT fk_rq_workspace FOREIGN KEY(workspace_id) REFERENCES course_workspaces(workspace_id) ON DELETE NO ACTION);
CREATE TABLE retrieval_results(retrieval_result_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),retrieval_query_id UNIQUEIDENTIFIER NOT NULL,chunk_id UNIQUEIDENTIFIER NOT NULL,document_id UNIQUEIDENTIFIER NOT NULL,result_rank INT NOT NULL,similarity_score FLOAT NOT NULL,rerank_score FLOAT,created_at DATETIME2 NOT NULL DEFAULT GETDATE(),CONSTRAINT fk_rr_query FOREIGN KEY(retrieval_query_id) REFERENCES retrieval_queries(retrieval_query_id) ON DELETE CASCADE,CONSTRAINT fk_rr_chunk FOREIGN KEY(chunk_id) REFERENCES document_chunks(chunk_id) ON DELETE NO ACTION,CONSTRAINT fk_rr_doc FOREIGN KEY(document_id) REFERENCES course_documents(document_id) ON DELETE NO ACTION);
CREATE TABLE answer_citations(citation_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),assistant_message_id UNIQUEIDENTIFIER NOT NULL,retrieval_result_id UNIQUEIDENTIFIER,document_id UNIQUEIDENTIFIER,chunk_id UNIQUEIDENTIFIER,citation_order INT NOT NULL DEFAULT 1,document_title NVARCHAR(255),page_start INT,page_end INT,quote_text NVARCHAR(MAX),created_at DATETIME2 NOT NULL DEFAULT GETDATE(),CONSTRAINT fk_cite_msg FOREIGN KEY(assistant_message_id) REFERENCES chat_messages(message_id) ON DELETE NO ACTION,CONSTRAINT fk_cite_rr FOREIGN KEY(retrieval_result_id) REFERENCES retrieval_results(retrieval_result_id) ON DELETE NO ACTION,CONSTRAINT fk_cite_doc FOREIGN KEY(document_id) REFERENCES course_documents(document_id) ON DELETE NO ACTION,CONSTRAINT fk_cite_chunk FOREIGN KEY(chunk_id) REFERENCES document_chunks(chunk_id) ON DELETE NO ACTION);
CREATE TABLE saved_notes(note_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),workspace_id UNIQUEIDENTIFIER NOT NULL,user_id UNIQUEIDENTIFIER,document_id UNIQUEIDENTIFIER,chat_session_id UNIQUEIDENTIFIER,note_title NVARCHAR(255) NOT NULL,note_content NVARCHAR(MAX) NOT NULL,note_type NVARCHAR(50) NOT NULL DEFAULT 'MANUAL',source_reference_json NVARCHAR(MAX),created_at DATETIME2 NOT NULL DEFAULT GETDATE(),updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),CONSTRAINT fk_note_workspace FOREIGN KEY(workspace_id) REFERENCES course_workspaces(workspace_id) ON DELETE CASCADE,CONSTRAINT fk_note_user FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE SET NULL);
CREATE TABLE evaluation_datasets(dataset_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),course_id UNIQUEIDENTIFIER NOT NULL,workspace_id UNIQUEIDENTIFIER,dataset_name NVARCHAR(255) NOT NULL,dataset_version NVARCHAR(50) NOT NULL DEFAULT 'v1',description NVARCHAR(MAX),created_by UNIQUEIDENTIFIER,created_at DATETIME2 NOT NULL DEFAULT GETDATE(),updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),CONSTRAINT fk_ds_course FOREIGN KEY(course_id) REFERENCES courses(course_id) ON DELETE CASCADE,CONSTRAINT fk_ds_workspace FOREIGN KEY(workspace_id) REFERENCES course_workspaces(workspace_id) ON DELETE NO ACTION,CONSTRAINT fk_ds_user FOREIGN KEY(created_by) REFERENCES users(user_id) ON DELETE SET NULL);
CREATE TABLE evaluation_questions(evaluation_question_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),dataset_id UNIQUEIDENTIFIER NOT NULL,course_id UNIQUEIDENTIFIER NOT NULL,chapter_id UNIQUEIDENTIFIER,question_no INT NOT NULL,question_text NVARCHAR(MAX) NOT NULL,ground_truth_answer NVARCHAR(MAX) NOT NULL,expected_document_id UNIQUEIDENTIFIER,expected_page INT,question_type NVARCHAR(50) NOT NULL DEFAULT 'FACTUAL',difficulty NVARCHAR(50) NOT NULL DEFAULT 'MEDIUM',created_at DATETIME2 NOT NULL DEFAULT GETDATE(),CONSTRAINT fk_eq_ds FOREIGN KEY(dataset_id) REFERENCES evaluation_datasets(dataset_id) ON DELETE CASCADE,CONSTRAINT fk_eq_course FOREIGN KEY(course_id) REFERENCES courses(course_id) ON DELETE NO ACTION);
CREATE TABLE experiments(experiment_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),dataset_id UNIQUEIDENTIFIER NOT NULL,course_id UNIQUEIDENTIFIER NOT NULL,workspace_id UNIQUEIDENTIFIER,experiment_name NVARCHAR(255) NOT NULL,experiment_type NVARCHAR(50) NOT NULL DEFAULT 'RAG',llm_model NVARCHAR(150) NOT NULL,embedding_model_id UNIQUEIDENTIFIER,chunking_strategy NVARCHAR(100),top_k INT NOT NULL DEFAULT 5,temperature FLOAT NOT NULL DEFAULT 0.2,fine_tuned_model_name NVARCHAR(255),config_json NVARCHAR(MAX),status NVARCHAR(50) NOT NULL DEFAULT 'PENDING',progress INT,ragas_status NVARCHAR(255),ragas_progress INT,ragas_error NVARCHAR(MAX),ragas_started_at DATETIME2,ragas_completed_at DATETIME2,local_duration_ms BIGINT,requested_batch_size INT,effective_batch_size INT,oom_fallback_count INT,error_message NVARCHAR(MAX),success_count INT,failure_count INT,dataset_checksum NVARCHAR(255),created_by UNIQUEIDENTIFIER,started_at DATETIME2,completed_at DATETIME2,created_at DATETIME2 NOT NULL DEFAULT GETDATE(),updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),CONSTRAINT fk_exp_ds FOREIGN KEY(dataset_id) REFERENCES evaluation_datasets(dataset_id) ON DELETE NO ACTION,CONSTRAINT fk_exp_course FOREIGN KEY(course_id) REFERENCES courses(course_id) ON DELETE NO ACTION);
CREATE TABLE experiment_results(experiment_result_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),experiment_id UNIQUEIDENTIFIER NOT NULL,evaluation_question_id UNIQUEIDENTIFIER NOT NULL,generated_answer NVARCHAR(MAX),retrieved_context_json NVARCHAR(MAX),citations_json NVARCHAR(MAX),faithfulness FLOAT,answer_relevance FLOAT,context_precision FLOAT,context_recall FLOAT,answer_correctness FLOAT,semantic_similarity FLOAT,provider_used NVARCHAR(255),base_model NVARCHAR(255),adapter_version NVARCHAR(255),embedding_model NVARCHAR(255),generation_mode NVARCHAR(255),dataset_version NVARCHAR(255),prompt_version NVARCHAR(255),metric_standard NVARCHAR(255),ragas_status NVARCHAR(255),ragas_error NVARCHAR(MAX),ragas_evaluated_at DATETIME2,judge_model NVARCHAR(255),evaluator_embedding NVARCHAR(255),source_hit BIT,page_hit BIT,refusal_correct BIT,throughput_qps FLOAT,peak_vram_bytes BIGINT,model_verification_status NVARCHAR(255),quality_gate_passed BIT,latency_ms INT,batch_latency_ms INT,effective_latency_ms INT,batch_size INT,input_tokens INT,output_tokens INT,total_tokens INT,cost DECIMAL(18,6),error_message NVARCHAR(MAX),created_at DATETIME2 NOT NULL DEFAULT GETDATE(),CONSTRAINT fk_res_exp FOREIGN KEY(experiment_id) REFERENCES experiments(experiment_id) ON DELETE CASCADE,CONSTRAINT fk_res_eq FOREIGN KEY(evaluation_question_id) REFERENCES evaluation_questions(evaluation_question_id) ON DELETE NO ACTION);
GO
CREATE VIEW v_experiment_dashboard AS SELECT e.experiment_id,e.experiment_name,e.experiment_type,e.status,e.llm_model,em.model_name AS embedding_model,e.chunking_strategy,e.top_k,COUNT(er.experiment_result_id) AS total_questions,AVG(er.faithfulness) AS avg_faithfulness,AVG(er.answer_relevance) AS avg_answer_relevance,AVG(er.context_precision) AS avg_context_precision,AVG(er.context_recall) AS avg_context_recall,AVG(er.answer_correctness) AS avg_answer_correctness,AVG(er.semantic_similarity) AS avg_semantic_similarity,AVG(CAST(er.latency_ms AS FLOAT)) AS avg_latency_ms,SUM(er.total_tokens) AS total_tokens,SUM(er.cost) AS total_cost FROM experiments e LEFT JOIN embedding_models em ON em.embedding_model_id=e.embedding_model_id LEFT JOIN experiment_results er ON er.experiment_id=e.experiment_id GROUP BY e.experiment_id,e.experiment_name,e.experiment_type,e.status,e.llm_model,em.model_name,e.chunking_strategy,e.top_k;
GO
INSERT INTO users(full_name,email,password_hash) VALUES (N'System Admin',N'admin@example.com',N'$2a$10$vvpg3kVXVyxfTbHEMoLqFeItAea9BmDvOjh2PIFeWbZqsQl0YIq3G'),(N'Teacher Demo',N'teacher@example.com',N'$2a$10$vvpg3kVXVyxfTbHEMoLqFeItAea9BmDvOjh2PIFeWbZqsQl0YIq3G'),(N'Student Demo',N'student@example.com',N'$2a$10$vvpg3kVXVyxfTbHEMoLqFeItAea9BmDvOjh2PIFeWbZqsQl0YIq3G'),(N'Researcher Demo',N'researcher@example.com',N'$2a$10$vvpg3kVXVyxfTbHEMoLqFeItAea9BmDvOjh2PIFeWbZqsQl0YIq3G');
INSERT INTO user_roles(user_id,role_name,permission_json) SELECT user_id,'ADMIN',N'{"all":true}' FROM users WHERE email=N'admin@example.com';
INSERT INTO user_roles(user_id,role_name,permission_json) SELECT user_id,'TEACHER',N'{"courses":true,"documents":true}' FROM users WHERE email=N'teacher@example.com';
INSERT INTO user_roles(user_id,role_name,permission_json) SELECT user_id,'STUDENT',N'{"chat":true,"notes":true}' FROM users WHERE email=N'student@example.com';
INSERT INTO user_roles(user_id,role_name,permission_json) SELECT user_id,'RESEARCHER',N'{"experiments":true}' FROM users WHERE email=N'researcher@example.com';
DECLARE @AdminId UNIQUEIDENTIFIER; SELECT @AdminId=user_id FROM users WHERE email=N'admin@example.com';
INSERT INTO courses(course_code,course_name,description,created_by) VALUES(N'AI101',N'Nhập môn Trí tuệ nhân tạo',N'Môn học demo cho Vietnamese CourseQA.',@AdminId);
DECLARE @CourseId UNIQUEIDENTIFIER; SELECT @CourseId=course_id FROM courses WHERE course_code=N'AI101';
INSERT INTO chapters(course_id,chapter_title,description,order_index) VALUES(@CourseId,N'Chương 1: Tổng quan về AI',N'Khái niệm và ứng dụng AI.',1),(@CourseId,N'Chương 2: Machine Learning',N'Học máy cơ bản.',2),(@CourseId,N'Chương 3: Neural Networks',N'Mạng nơ-ron.',3);
DECLARE @TeacherId UNIQUEIDENTIFIER; SELECT @TeacherId=user_id FROM users WHERE email=N'teacher@example.com';
INSERT INTO course_workspaces(course_id,owner_user_id,workspace_title,description,visibility) VALUES(@CourseId,@TeacherId,N'AI101 Course Workspace',N'Không gian học tập AI101.',N'COURSE');
INSERT INTO embedding_models(model_name,provider,dimension,is_local,description,config_json) VALUES(N'multilingual-e5-base',N'Local Demo',128,1,N'Demo embedding model.',N'{}'),(N'text-embedding-3-small',N'OpenAI',1536,0,N'OpenAI embedding model reference.',N'{}'),(N'PhoBERT-base',N'VinAI',768,1,N'Vietnamese model reference.',N'{}'),(N'bge-m3',N'BAAI',1024,1,N'Multilingual model reference.',N'{}'),(N'paraphrase-multilingual-MiniLM-L12-v2-onnx',N'FastEmbed ONNX',384,1,N'Offline multilingual semantic embeddings for Vietnamese, Japanese and English document retrieval.',N'{"runtime":"onnxruntime","pooling":"mean","normalized":true}');


GO

-- ============================================================
-- V20260713__semester_workspace_flow.sql
-- ============================================================
-- Additive migration for Flow 2. Run after VietnameseCourseQA20DB.sql.
CREATE TABLE semester_workspaces (
  semester_workspace_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
  semester_code NVARCHAR(50) NOT NULL UNIQUE,
  semester_name NVARCHAR(255) NOT NULL,
  start_date DATE NULL, end_date DATE NULL,
  status NVARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  created_by UNIQUEIDENTIFIER NULL,
  created_at DATETIME2 NOT NULL DEFAULT GETDATE(), updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),
  CONSTRAINT chk_semester_status CHECK(status IN ('DRAFT','ACTIVE','ARCHIVED')),
  CONSTRAINT fk_semester_creator FOREIGN KEY(created_by) REFERENCES users(user_id) ON DELETE SET NULL
);
ALTER TABLE courses ADD semester_workspace_id UNIQUEIDENTIFIER NULL, status NVARCHAR(20) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE courses ADD CONSTRAINT fk_courses_semester FOREIGN KEY(semester_workspace_id) REFERENCES semester_workspaces(semester_workspace_id);
CREATE TABLE course_memberships (
  course_membership_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(), course_id UNIQUEIDENTIFIER NOT NULL,
  user_id UNIQUEIDENTIFIER NOT NULL, membership_role NVARCHAR(20) NOT NULL DEFAULT 'STUDENT',
  status NVARCHAR(20) NOT NULL DEFAULT 'ACTIVE', assigned_by UNIQUEIDENTIFIER NULL,
  assigned_at DATETIME2 NOT NULL DEFAULT GETDATE(),
  CONSTRAINT uq_course_member UNIQUE(course_id,user_id),
  CONSTRAINT fk_membership_course FOREIGN KEY(course_id) REFERENCES courses(course_id) ON DELETE CASCADE,
  CONSTRAINT fk_membership_user FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
CREATE TABLE document_chapter_suggestions (
  suggestion_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(), document_id UNIQUEIDENTIFIER NOT NULL,
  suggested_title NVARCHAR(255) NOT NULL, page_start INT NOT NULL, page_end INT NOT NULL,
  confidence FLOAT NULL, status NVARCHAR(20) NOT NULL DEFAULT 'PENDING',
  CONSTRAINT fk_suggestion_document FOREIGN KEY(document_id) REFERENCES course_documents(document_id) ON DELETE CASCADE
);
CREATE TABLE document_chapter_ranges (
  document_chapter_range_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(), document_id UNIQUEIDENTIFIER NOT NULL,
  chapter_id UNIQUEIDENTIFIER NOT NULL, page_start INT NOT NULL, page_end INT NOT NULL,
  -- SQL Server rejects two cascading paths from course_documents/chapters. The service removes ranges explicitly.
  CONSTRAINT fk_range_document FOREIGN KEY(document_id) REFERENCES course_documents(document_id) ON DELETE NO ACTION,
  CONSTRAINT fk_range_chapter FOREIGN KEY(chapter_id) REFERENCES chapters(chapter_id) ON DELETE NO ACTION
);
-- Backfill existing records before making semester_workspace_id mandatory in a later release.

GO

-- ============================================================
-- V20260715__flow2_security_and_course_scope.sql
-- ============================================================
-- Allow the same course code in different semesters, while keeping it unique inside one semester.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

DECLARE @constraintName SYSNAME;
SELECT TOP 1 @constraintName = kc.name
FROM sys.key_constraints kc
JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE kc.parent_object_id = OBJECT_ID('courses')
  AND kc.type = 'UQ'
  AND c.name = 'course_code';

IF @constraintName IS NOT NULL
    EXEC('ALTER TABLE courses DROP CONSTRAINT [' + @constraintName + ']');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('courses') AND name = 'uq_courses_semester_code')
    CREATE UNIQUE INDEX uq_courses_semester_code
        ON courses(semester_workspace_id, course_code)
        WHERE semester_workspace_id IS NOT NULL;

GO

-- ============================================================
-- V20260716__chat_retrieval_scopes.sql
-- ============================================================
-- Additive migration for fixed chat retrieval scopes.
USE VietnameseCourseQA20DB;
GO

IF COL_LENGTH('chat_sessions', 'scope_type') IS NULL
    ALTER TABLE chat_sessions ADD scope_type NVARCHAR(20) NOT NULL
        CONSTRAINT df_chat_sessions_scope_type DEFAULT 'COURSE';

IF COL_LENGTH('chat_sessions', 'semester_workspace_id') IS NULL
    ALTER TABLE chat_sessions ADD semester_workspace_id UNIQUEIDENTIFIER NULL;

ALTER TABLE chat_sessions ALTER COLUMN workspace_id UNIQUEIDENTIFIER NULL;
ALTER TABLE chat_sessions ALTER COLUMN course_id UNIQUEIDENTIFIER NULL;
GO

UPDATE session
SET semester_workspace_id = course.semester_workspace_id,
    scope_type = COALESCE(NULLIF(session.scope_type, ''), 'COURSE')
FROM chat_sessions session
LEFT JOIN courses course ON course.course_id = session.course_id
WHERE session.semester_workspace_id IS NULL OR session.scope_type IS NULL OR session.scope_type = '';

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'fk_chat_semester')
    ALTER TABLE chat_sessions ADD CONSTRAINT fk_chat_semester
        FOREIGN KEY (semester_workspace_id) REFERENCES semester_workspaces(semester_workspace_id);

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'chk_chat_scope_type')
    ALTER TABLE chat_sessions ADD CONSTRAINT chk_chat_scope_type
        CHECK (scope_type IN ('DOCUMENTS', 'COURSE', 'SEMESTER'));

IF OBJECT_ID('chat_session_documents', 'U') IS NULL
BEGIN
    CREATE TABLE chat_session_documents (
        chat_session_document_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
        chat_session_id UNIQUEIDENTIFIER NOT NULL,
        document_id UNIQUEIDENTIFIER NOT NULL,
        CONSTRAINT uq_chat_session_document UNIQUE(chat_session_id, document_id),
        CONSTRAINT fk_chat_session_document_session FOREIGN KEY(chat_session_id)
            REFERENCES chat_sessions(chat_session_id) ON DELETE CASCADE,
        CONSTRAINT fk_chat_session_document_document FOREIGN KEY(document_id)
            REFERENCES course_documents(document_id) ON DELETE NO ACTION
    );
END;

IF COL_LENGTH('retrieval_queries', 'scope_type') IS NULL
    ALTER TABLE retrieval_queries ADD scope_type NVARCHAR(20) NOT NULL
        CONSTRAINT df_retrieval_queries_scope_type DEFAULT 'COURSE';

IF COL_LENGTH('retrieval_queries', 'semester_workspace_id') IS NULL
    ALTER TABLE retrieval_queries ADD semester_workspace_id UNIQUEIDENTIFIER NULL;

ALTER TABLE retrieval_queries ALTER COLUMN workspace_id UNIQUEIDENTIFIER NULL;
GO

UPDATE query
SET query.scope_type = COALESCE(NULLIF(session.scope_type, ''), 'COURSE'),
    query.semester_workspace_id = session.semester_workspace_id
FROM retrieval_queries query
JOIN chat_sessions session ON session.chat_session_id = query.chat_session_id
WHERE query.semester_workspace_id IS NULL OR query.scope_type IS NULL OR query.scope_type = '';

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'fk_rq_semester')
    ALTER TABLE retrieval_queries ADD CONSTRAINT fk_rq_semester
        FOREIGN KEY (semester_workspace_id) REFERENCES semester_workspaces(semester_workspace_id);
GO

GO

-- ============================================================
-- V20260717__flow5_dataset_snapshot_and_async_benchmark.sql
-- ============================================================
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH('evaluation_datasets', 'semester_workspace_id') IS NULL
    ALTER TABLE evaluation_datasets ADD semester_workspace_id UNIQUEIDENTIFIER NULL;
IF COL_LENGTH('evaluation_datasets', 'status') IS NULL
    ALTER TABLE evaluation_datasets ADD status NVARCHAR(20) NOT NULL CONSTRAINT df_evaluation_datasets_status DEFAULT 'DRAFT';
IF COL_LENGTH('evaluation_datasets', 'validation_error') IS NULL
    ALTER TABLE evaluation_datasets ADD validation_error NVARCHAR(MAX) NULL;
IF COL_LENGTH('evaluation_datasets', 'checksum') IS NULL
    ALTER TABLE evaluation_datasets ADD checksum NVARCHAR(64) NULL;

EXEC sp_executesql N'
UPDATE dataset
SET semester_workspace_id = course.semester_workspace_id
FROM evaluation_datasets dataset
JOIN courses course ON course.course_id = dataset.course_id
WHERE dataset.semester_workspace_id IS NULL;';

UPDATE dataset
SET workspace_id = selected.workspace_id
FROM evaluation_datasets dataset
CROSS APPLY (
    SELECT TOP 1 workspace.workspace_id
    FROM course_workspaces workspace
    WHERE workspace.course_id = dataset.course_id AND workspace.is_active = 1
    ORDER BY workspace.created_at DESC
) selected
WHERE dataset.workspace_id IS NULL;

IF OBJECT_ID('evaluation_dataset_documents', 'U') IS NULL
BEGIN
    CREATE TABLE evaluation_dataset_documents (
        dataset_id UNIQUEIDENTIFIER NOT NULL,
        document_id UNIQUEIDENTIFIER NOT NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT df_evaluation_dataset_documents_created DEFAULT GETDATE(),
        CONSTRAINT pk_evaluation_dataset_documents PRIMARY KEY (dataset_id, document_id),
        CONSTRAINT fk_evaluation_dataset_documents_dataset FOREIGN KEY (dataset_id)
            REFERENCES evaluation_datasets(dataset_id) ON DELETE CASCADE,
        CONSTRAINT fk_evaluation_dataset_documents_document FOREIGN KEY (document_id)
            REFERENCES course_documents(document_id) ON DELETE NO ACTION
    );
END;

INSERT INTO evaluation_dataset_documents(dataset_id, document_id)
SELECT dataset.dataset_id, document.document_id
FROM evaluation_datasets dataset
JOIN course_documents document ON document.course_id = dataset.course_id
    AND document.processing_status = 'PROCESSED'
WHERE NOT EXISTS (
    SELECT 1 FROM evaluation_dataset_documents existing
    WHERE existing.dataset_id = dataset.dataset_id AND existing.document_id = document.document_id
);

EXEC sp_executesql N'
UPDATE dataset
SET status = ''INVALID'',
    validation_error = CASE
        WHEN dataset.semester_workspace_id IS NULL THEN ''Course is not assigned to a semester.''
        WHEN dataset.workspace_id IS NULL THEN ''Course has no active knowledge base.''
        WHEN NOT EXISTS (SELECT 1 FROM evaluation_dataset_documents link WHERE link.dataset_id = dataset.dataset_id)
            THEN ''Dataset has no processed document snapshot.''
        ELSE dataset.validation_error
    END
FROM evaluation_datasets dataset
WHERE dataset.semester_workspace_id IS NULL
   OR dataset.workspace_id IS NULL
   OR NOT EXISTS (SELECT 1 FROM evaluation_dataset_documents link WHERE link.dataset_id = dataset.dataset_id);';

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'fk_evaluation_datasets_semester')
    EXEC sp_executesql N'ALTER TABLE evaluation_datasets ADD CONSTRAINT fk_evaluation_datasets_semester
        FOREIGN KEY (semester_workspace_id) REFERENCES semester_workspaces(semester_workspace_id);';

IF COL_LENGTH('experiments', 'progress') IS NULL
    ALTER TABLE experiments ADD progress INT NOT NULL CONSTRAINT df_experiments_progress DEFAULT 0;
IF COL_LENGTH('experiments', 'error_message') IS NULL
    ALTER TABLE experiments ADD error_message NVARCHAR(MAX) NULL;
IF COL_LENGTH('experiments', 'success_count') IS NULL
    ALTER TABLE experiments ADD success_count INT NOT NULL CONSTRAINT df_experiments_success_count DEFAULT 0;
IF COL_LENGTH('experiments', 'failure_count') IS NULL
    ALTER TABLE experiments ADD failure_count INT NOT NULL CONSTRAINT df_experiments_failure_count DEFAULT 0;
IF COL_LENGTH('experiments', 'dataset_checksum') IS NULL
    ALTER TABLE experiments ADD dataset_checksum NVARCHAR(64) NULL;

EXEC sp_executesql N'
UPDATE experiments
SET status = ''FAILED'', progress = 0,
    error_message = COALESCE(error_message, ''Run was interrupted before the Flow 5 async migration.''),
    completed_at = COALESCE(completed_at, GETDATE()), updated_at = GETDATE()
WHERE status = ''RUNNING'';';

COMMIT TRANSACTION;

GO

-- ============================================================
-- V20260718__personal_document_library.sql
-- ============================================================
-- Personal document library and moderated course sharing.
USE VietnameseCourseQA20DB;
GO
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('course_workspaces') AND name = 'course_id' AND is_nullable = 0)
    ALTER TABLE course_workspaces ALTER COLUMN course_id UNIQUEIDENTIFIER NULL;
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('course_documents') AND name = 'course_id' AND is_nullable = 0)
    ALTER TABLE course_documents ALTER COLUMN course_id UNIQUEIDENTIFIER NULL;
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('document_chunks') AND name = 'course_id' AND is_nullable = 0)
    ALTER TABLE document_chunks ALTER COLUMN course_id UNIQUEIDENTIFIER NULL;
GO

IF COL_LENGTH('course_documents', 'document_scope') IS NULL
    ALTER TABLE course_documents ADD document_scope NVARCHAR(20) NULL;
IF COL_LENGTH('course_documents', 'review_status') IS NULL
    ALTER TABLE course_documents ADD review_status NVARCHAR(20) NULL;
IF COL_LENGTH('course_documents', 'target_course_id') IS NULL
    ALTER TABLE course_documents ADD target_course_id UNIQUEIDENTIFIER NULL;
IF COL_LENGTH('course_documents', 'submitted_at') IS NULL
    ALTER TABLE course_documents ADD submitted_at DATETIME2 NULL;
IF COL_LENGTH('course_documents', 'reviewed_by') IS NULL
    ALTER TABLE course_documents ADD reviewed_by UNIQUEIDENTIFIER NULL;
IF COL_LENGTH('course_documents', 'reviewed_at') IS NULL
    ALTER TABLE course_documents ADD reviewed_at DATETIME2 NULL;
IF COL_LENGTH('course_documents', 'rejection_reason') IS NULL
    ALTER TABLE course_documents ADD rejection_reason NVARCHAR(MAX) NULL;
GO

UPDATE course_documents
SET document_scope = COALESCE(NULLIF(document_scope, ''), 'COURSE'),
    review_status = COALESCE(NULLIF(review_status, ''), 'APPROVED');

ALTER TABLE course_documents ALTER COLUMN document_scope NVARCHAR(20) NOT NULL;
ALTER TABLE course_documents ALTER COLUMN review_status NVARCHAR(20) NOT NULL;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.default_constraints dc
    JOIN sys.columns c
      ON c.object_id = dc.parent_object_id
     AND c.column_id = dc.parent_column_id
    WHERE dc.parent_object_id = OBJECT_ID('course_documents')
      AND c.name = 'document_scope'
)
    ALTER TABLE course_documents ADD CONSTRAINT df_course_documents_scope DEFAULT 'COURSE' FOR document_scope;
IF NOT EXISTS (
    SELECT 1
    FROM sys.default_constraints dc
    JOIN sys.columns c
      ON c.object_id = dc.parent_object_id
     AND c.column_id = dc.parent_column_id
    WHERE dc.parent_object_id = OBJECT_ID('course_documents')
      AND c.name = 'review_status'
)
    ALTER TABLE course_documents ADD CONSTRAINT df_course_documents_review_status DEFAULT 'APPROVED' FOR review_status;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'chk_course_documents_scope')
    ALTER TABLE course_documents ADD CONSTRAINT chk_course_documents_scope
        CHECK (document_scope IN ('PERSONAL', 'COURSE'));
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'chk_course_documents_review_status')
    ALTER TABLE course_documents ADD CONSTRAINT chk_course_documents_review_status
        CHECK (review_status IN ('NOT_SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED'));

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'fk_docs_target_course')
    ALTER TABLE course_documents ADD CONSTRAINT fk_docs_target_course
        FOREIGN KEY (target_course_id) REFERENCES courses(course_id) ON DELETE NO ACTION;
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'fk_docs_reviewed_by')
    ALTER TABLE course_documents ADD CONSTRAINT fk_docs_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES users(user_id) ON DELETE NO ACTION;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uq_personal_workspace_owner')
    CREATE UNIQUE INDEX uq_personal_workspace_owner
        ON course_workspaces(owner_user_id)
        WHERE course_id IS NULL AND owner_user_id IS NOT NULL AND visibility = 'PRIVATE';
GO

IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'chk_chat_scope_type')
    ALTER TABLE chat_sessions DROP CONSTRAINT chk_chat_scope_type;
ALTER TABLE chat_sessions ADD CONSTRAINT chk_chat_scope_type
    CHECK (scope_type IN ('PERSONAL', 'DOCUMENTS', 'COURSE', 'SEMESTER'));
GO

GO

-- ============================================================
-- V20260722__offline_multilingual_rag.sql
-- ============================================================
SET NOCOUNT ON;

IF NOT EXISTS (
    SELECT 1
    FROM embedding_models
    WHERE model_name = N'paraphrase-multilingual-MiniLM-L12-v2-onnx'
)
BEGIN
    INSERT INTO embedding_models(
        model_name,
        provider,
        dimension,
        is_local,
        description,
        config_json,
        is_active,
        created_at
    )
    VALUES (
        N'paraphrase-multilingual-MiniLM-L12-v2-onnx',
        N'FastEmbed ONNX',
        384,
        1,
        N'Offline multilingual semantic embeddings for Vietnamese, Japanese and English document retrieval.',
        N'{"runtime":"onnxruntime","pooling":"mean","normalized":true}',
        1,
        GETDATE()
    );
END;

GO

-- ============================================================
-- V20260725__chat_workspace_redesign.sql
-- ============================================================
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;

IF COL_LENGTH('chat_sessions', 'is_pinned') IS NULL
    ALTER TABLE chat_sessions ADD is_pinned BIT NOT NULL
        CONSTRAINT df_chat_sessions_is_pinned DEFAULT 0;

IF COL_LENGTH('chat_sessions', 'pinned_at') IS NULL
    ALTER TABLE chat_sessions ADD pinned_at DATETIME2 NULL;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'ix_chat_sessions_owner_history'
      AND object_id = OBJECT_ID('chat_sessions')
)
    CREATE INDEX ix_chat_sessions_owner_history
        ON chat_sessions(user_id, is_active, is_pinned, pinned_at, updated_at);

GO

-- ============================================================
-- V20260728__cleanup_evaluation_benchmark_chat.sql
-- ============================================================
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    SELECT chat_session_id
    INTO #BenchmarkSessions
    FROM chat_sessions
    WHERE session_title = N'Evaluation benchmark';

    SELECT message_id
    INTO #BenchmarkMessages
    FROM chat_messages
    WHERE chat_session_id IN (SELECT chat_session_id FROM #BenchmarkSessions);

    SELECT
        (SELECT COUNT(*) FROM #BenchmarkSessions) AS sessions_before,
        (SELECT COUNT(*) FROM #BenchmarkMessages) AS messages_before;

    DELETE FROM answer_citations
    WHERE assistant_message_id IN (SELECT message_id FROM #BenchmarkMessages)
       OR retrieval_result_id IN (
            SELECT rr.retrieval_result_id
            FROM retrieval_results rr
            JOIN retrieval_queries rq
              ON rq.retrieval_query_id = rr.retrieval_query_id
            WHERE rq.chat_session_id IN (SELECT chat_session_id FROM #BenchmarkSessions)
       );

    DELETE FROM retrieval_results
    WHERE retrieval_query_id IN (
        SELECT retrieval_query_id
        FROM retrieval_queries
        WHERE chat_session_id IN (SELECT chat_session_id FROM #BenchmarkSessions)
    );

    DELETE FROM retrieval_queries
    WHERE chat_session_id IN (SELECT chat_session_id FROM #BenchmarkSessions);

    DELETE FROM chat_messages
    WHERE chat_session_id IN (SELECT chat_session_id FROM #BenchmarkSessions);

    DELETE FROM chat_session_documents
    WHERE chat_session_id IN (SELECT chat_session_id FROM #BenchmarkSessions);

    DELETE FROM chat_sessions
    WHERE chat_session_id IN (SELECT chat_session_id FROM #BenchmarkSessions);

    SELECT
        (SELECT COUNT(*) FROM chat_sessions WHERE session_title = N'Evaluation benchmark')
            AS sessions_after,
        (SELECT COUNT(*)
         FROM chat_messages message
         JOIN chat_sessions session
           ON session.chat_session_id = message.chat_session_id
         WHERE session.session_title = N'Evaluation benchmark')
            AS messages_after;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

GO

-- ============================================================
-- V20260728__research_model_ragas_metadata.sql
-- ============================================================
IF COL_LENGTH('experiment_results', 'provider_used') IS NULL
    ALTER TABLE experiment_results ADD provider_used NVARCHAR(100) NULL;
IF COL_LENGTH('experiment_results', 'base_model') IS NULL
    ALTER TABLE experiment_results ADD base_model NVARCHAR(255) NULL;
IF COL_LENGTH('experiment_results', 'adapter_version') IS NULL
    ALTER TABLE experiment_results ADD adapter_version NVARCHAR(255) NULL;
IF COL_LENGTH('experiment_results', 'embedding_model') IS NULL
    ALTER TABLE experiment_results ADD embedding_model NVARCHAR(255) NULL;
IF COL_LENGTH('experiment_results', 'generation_mode') IS NULL
    ALTER TABLE experiment_results ADD generation_mode NVARCHAR(50) NULL;
IF COL_LENGTH('experiment_results', 'dataset_version') IS NULL
    ALTER TABLE experiment_results ADD dataset_version NVARCHAR(100) NULL;
IF COL_LENGTH('experiment_results', 'prompt_version') IS NULL
    ALTER TABLE experiment_results ADD prompt_version NVARCHAR(100) NULL;
IF COL_LENGTH('experiment_results', 'metric_standard') IS NULL
    ALTER TABLE experiment_results ADD metric_standard NVARCHAR(50) NULL;
IF COL_LENGTH('experiment_results', 'judge_model') IS NULL
    ALTER TABLE experiment_results ADD judge_model NVARCHAR(255) NULL;
IF COL_LENGTH('experiment_results', 'evaluator_embedding') IS NULL
    ALTER TABLE experiment_results ADD evaluator_embedding NVARCHAR(255) NULL;
IF COL_LENGTH('evaluation_questions', 'expected_source') IS NULL
    ALTER TABLE evaluation_questions ADD expected_source NVARCHAR(500) NULL;
IF COL_LENGTH('evaluation_questions', 'evidence_quote') IS NULL
    ALTER TABLE evaluation_questions ADD evidence_quote NVARCHAR(MAX) NULL;
IF COL_LENGTH('evaluation_questions', 'chapter_label') IS NULL
    ALTER TABLE evaluation_questions ADD chapter_label NVARCHAR(255) NULL;
IF COL_LENGTH('evaluation_questions', 'is_out_of_scope') IS NULL
    ALTER TABLE evaluation_questions ADD is_out_of_scope BIT NOT NULL
        CONSTRAINT DF_evaluation_questions_is_out_of_scope DEFAULT 0;
IF COL_LENGTH('experiment_results', 'source_hit') IS NULL
    ALTER TABLE experiment_results ADD source_hit BIT NULL;
IF COL_LENGTH('experiment_results', 'page_hit') IS NULL
    ALTER TABLE experiment_results ADD page_hit BIT NULL;
IF COL_LENGTH('experiment_results', 'refusal_correct') IS NULL
    ALTER TABLE experiment_results ADD refusal_correct BIT NULL;
IF COL_LENGTH('experiment_results', 'throughput_qps') IS NULL
    ALTER TABLE experiment_results ADD throughput_qps FLOAT NULL;
IF COL_LENGTH('experiment_results', 'peak_vram_bytes') IS NULL
    ALTER TABLE experiment_results ADD peak_vram_bytes BIGINT NULL;
IF COL_LENGTH('experiment_results', 'model_verification_status') IS NULL
    ALTER TABLE experiment_results ADD model_verification_status NVARCHAR(50) NULL;
IF COL_LENGTH('experiment_results', 'quality_gate_passed') IS NULL
    ALTER TABLE experiment_results ADD quality_gate_passed BIT NULL;

GO

-- ============================================================
-- V20260729__chat_rag_performance.sql
-- ============================================================
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    ;WITH ranked_embeddings AS (
        SELECT
            chunk_embedding_id,
            ROW_NUMBER() OVER (
                PARTITION BY embedding_model_id, chunk_id
                ORDER BY created_at DESC, chunk_embedding_id DESC
            ) AS duplicate_rank
        FROM chunk_embeddings
    )
    DELETE FROM ranked_embeddings
    WHERE duplicate_rank > 1;

    IF COL_LENGTH('chunk_embeddings', 'embedding_compressed') IS NULL
        ALTER TABLE chunk_embeddings ADD embedding_compressed VARBINARY(MAX) NULL;

    EXEC sys.sp_executesql N'
        UPDATE chunk_embeddings
        SET embedding_compressed = COMPRESS(CONVERT(VARCHAR(MAX), embedding_json))
        WHERE embedding_compressed IS NULL
          AND embedding_json IS NOT NULL;
    ';

    IF COL_LENGTH('document_chunks', 'content_compressed') IS NULL
        ALTER TABLE document_chunks ADD content_compressed VARBINARY(MAX) NULL;

    EXEC sys.sp_executesql N'
        UPDATE document_chunks
        SET content_compressed = COMPRESS(CONVERT(VARBINARY(MAX), content))
        WHERE content IS NOT NULL;
    ';

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE name = 'ux_chunk_embeddings_model_chunk'
          AND object_id = OBJECT_ID('chunk_embeddings')
    )
        CREATE UNIQUE INDEX ux_chunk_embeddings_model_chunk
            ON chunk_embeddings(embedding_model_id, chunk_id);

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE name = 'ix_document_chunks_document_created'
          AND object_id = OBJECT_ID('document_chunks')
    )
        CREATE INDEX ix_document_chunks_document_created
            ON document_chunks(document_id, created_at)
            INCLUDE (chunk_id, workspace_id, course_id, chunk_index, page_start, page_end);

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE name = 'ix_document_chunks_workspace_created'
          AND object_id = OBJECT_ID('document_chunks')
    )
        CREATE INDEX ix_document_chunks_workspace_created
            ON document_chunks(workspace_id, created_at)
            INCLUDE (chunk_id, document_id, course_id, chunk_index, page_start, page_end);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

GO

-- ============================================================
-- V20260730__rag_quality_and_processing_trace.sql
-- ============================================================
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH('course_documents', 'indexing_status') IS NULL
        ALTER TABLE course_documents ADD indexing_status NVARCHAR(30) NULL;
    IF COL_LENGTH('course_documents', 'indexed_embedding_model_id') IS NULL
        ALTER TABLE course_documents ADD indexed_embedding_model_id UNIQUEIDENTIFIER NULL;
    IF COL_LENGTH('course_documents', 'indexed_model_version') IS NULL
        ALTER TABLE course_documents ADD indexed_model_version NVARCHAR(255) NULL;
    IF COL_LENGTH('course_documents', 'indexed_at') IS NULL
        ALTER TABLE course_documents ADD indexed_at DATETIME2 NULL;
    IF COL_LENGTH('course_documents', 'index_error') IS NULL
        ALTER TABLE course_documents ADD index_error NVARCHAR(MAX) NULL;

    IF COL_LENGTH('chat_messages', 'answer_depth') IS NULL
        ALTER TABLE chat_messages ADD answer_depth NVARCHAR(20) NULL;
    IF COL_LENGTH('chat_messages', 'question_intent') IS NULL
        ALTER TABLE chat_messages ADD question_intent NVARCHAR(40) NULL;
    IF COL_LENGTH('chat_messages', 'processing_trace_json') IS NULL
        ALTER TABLE chat_messages ADD processing_trace_json NVARCHAR(MAX) NULL;

    EXEC sys.sp_executesql N'
        UPDATE d
        SET indexing_status = CASE
                WHEN d.processing_status <> ''PROCESSED'' THEN ''FAILED''
                WHEN stats.chunk_count > 0
                 AND stats.chunk_count = stats.embedded_chunk_count THEN ''INDEXED''
                ELSE ''PENDING''
            END,
            indexed_at = CASE
                WHEN stats.chunk_count > 0
                 AND stats.chunk_count = stats.embedded_chunk_count
                    THEN COALESCE(d.indexed_at, GETDATE())
                ELSE d.indexed_at
            END
        FROM course_documents d
        OUTER APPLY (
            SELECT
                COUNT(DISTINCT c.chunk_id) AS chunk_count,
                COUNT(DISTINCT CASE WHEN e.chunk_id IS NOT NULL THEN c.chunk_id END)
                    AS embedded_chunk_count
            FROM document_chunks c
            LEFT JOIN chunk_embeddings e ON e.chunk_id = c.chunk_id
            WHERE c.document_id = d.document_id
        ) stats
        WHERE d.indexing_status IS NULL;
    ';

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

GO

-- ============================================================
-- V20260731__parallel_benchmark_and_background_ragas.sql
-- ============================================================
IF COL_LENGTH('experiments', 'ragas_status') IS NULL
    ALTER TABLE experiments ADD ragas_status NVARCHAR(255) NULL;
IF COL_LENGTH('experiments', 'ragas_progress') IS NULL
    ALTER TABLE experiments ADD ragas_progress INT NULL;
IF COL_LENGTH('experiments', 'ragas_error') IS NULL
    ALTER TABLE experiments ADD ragas_error NVARCHAR(MAX) NULL;
IF COL_LENGTH('experiments', 'ragas_started_at') IS NULL
    ALTER TABLE experiments ADD ragas_started_at DATETIME2 NULL;
IF COL_LENGTH('experiments', 'ragas_completed_at') IS NULL
    ALTER TABLE experiments ADD ragas_completed_at DATETIME2 NULL;
IF COL_LENGTH('experiments', 'local_duration_ms') IS NULL
    ALTER TABLE experiments ADD local_duration_ms BIGINT NULL;
IF COL_LENGTH('experiments', 'requested_batch_size') IS NULL
    ALTER TABLE experiments ADD requested_batch_size INT NULL;
IF COL_LENGTH('experiments', 'effective_batch_size') IS NULL
    ALTER TABLE experiments ADD effective_batch_size INT NULL;
IF COL_LENGTH('experiments', 'oom_fallback_count') IS NULL
    ALTER TABLE experiments ADD oom_fallback_count INT NULL;

IF COL_LENGTH('experiment_results', 'ragas_status') IS NULL
    ALTER TABLE experiment_results ADD ragas_status NVARCHAR(255) NULL;
IF COL_LENGTH('experiment_results', 'ragas_error') IS NULL
    ALTER TABLE experiment_results ADD ragas_error NVARCHAR(MAX) NULL;
IF COL_LENGTH('experiment_results', 'ragas_evaluated_at') IS NULL
    ALTER TABLE experiment_results ADD ragas_evaluated_at DATETIME2 NULL;

-- SQL Server binds the UPDATE statements below before evaluating the ALTERs
-- above. Split the batch so this migration also works on legacy backups where
-- these RAGAS columns do not exist yet.
GO

UPDATE experiments
SET ragas_status = CASE
        WHEN status = 'COMPLETED' THEN 'COMPLETED'
        WHEN status = 'FAILED' THEN 'FAILED'
        ELSE 'PENDING'
    END,
    ragas_progress = CASE WHEN status IN ('COMPLETED', 'FAILED') THEN 100 ELSE 0 END
WHERE ragas_status IS NULL;

UPDATE experiment_results
SET ragas_status = CASE
        WHEN metric_standard = 'RAGAS_OFFICIAL' THEN 'COMPLETED'
        WHEN error_message IS NOT NULL THEN 'FAILED'
        ELSE 'PENDING'
    END
WHERE ragas_status IS NULL;

GO

-- ============================================================
-- V20260801__document_processing_jobs.sql
-- ============================================================
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'processing_jobs')
        CREATE TABLE processing_jobs(
            job_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
            document_id UNIQUEIDENTIFIER NOT NULL,
            job_type NVARCHAR(20) NOT NULL DEFAULT 'UPLOAD',
            status NVARCHAR(30) NOT NULL DEFAULT 'QUEUED',
            progress_step NVARCHAR(50) NULL,
            error_code NVARCHAR(50) NULL,
            error_message NVARCHAR(MAX) NULL,
            created_by UNIQUEIDENTIFIER NULL,
            created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            started_at DATETIME2 NULL,
            completed_at DATETIME2 NULL,
            updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            CONSTRAINT chk_processing_jobs_type CHECK(job_type IN('UPLOAD','REINDEX','RETRY')),
            CONSTRAINT fk_processing_jobs_document FOREIGN KEY(document_id) REFERENCES course_documents(document_id) ON DELETE CASCADE,
            CONSTRAINT fk_processing_jobs_created_by FOREIGN KEY(created_by) REFERENCES users(user_id) ON DELETE SET NULL
        );

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_processing_jobs_document')
        CREATE INDEX ix_processing_jobs_document ON processing_jobs(document_id, created_at DESC);

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_processing_jobs_status')
        CREATE INDEX ix_processing_jobs_status ON processing_jobs(status);

    IF COL_LENGTH('course_documents', 'content_hash') IS NULL
        ALTER TABLE course_documents ADD content_hash NVARCHAR(64) NULL;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_course_documents_owner_hash')
        CREATE INDEX ix_course_documents_owner_hash ON course_documents(uploaded_by, content_hash);

    IF COL_LENGTH('document_chunks', 'heading_path') IS NULL
        ALTER TABLE document_chunks ADD heading_path NVARCHAR(500) NULL;
    IF COL_LENGTH('document_chunks', 'chunk_version') IS NULL
        ALTER TABLE document_chunks ADD chunk_version INT NOT NULL DEFAULT 1;
    IF COL_LENGTH('document_chunks', 'is_active') IS NULL
        ALTER TABLE document_chunks ADD is_active BIT NOT NULL DEFAULT 1;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_document_chunks_active')
        CREATE INDEX ix_document_chunks_active ON document_chunks(document_id, is_active);

    IF COL_LENGTH('document_pages', 'ocr_applied') IS NULL
        ALTER TABLE document_pages ADD ocr_applied BIT NOT NULL DEFAULT 0;
    IF COL_LENGTH('document_pages', 'ocr_confidence') IS NULL
        ALTER TABLE document_pages ADD ocr_confidence FLOAT NULL;
    IF COL_LENGTH('document_pages', 'heading_path') IS NULL
        ALTER TABLE document_pages ADD heading_path NVARCHAR(500) NULL;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

GO

-- ============================================================
-- V20260802__evaluation_report_exports.sql
-- ============================================================
IF OBJECT_ID('evaluation_reports', 'U') IS NULL
BEGIN
    CREATE TABLE evaluation_reports (
        report_id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
        dataset_id UNIQUEIDENTIFIER NOT NULL,
        rag_experiment_id UNIQUEIDENTIFIER NOT NULL,
        fine_tuned_experiment_id UNIQUEIDENTIFIER NOT NULL,
        language NVARCHAR(8) NOT NULL DEFAULT N'vi',
        title NVARCHAR(255) NOT NULL,
        status NVARCHAR(32) NOT NULL DEFAULT N'QUEUED',
        progress INT NOT NULL DEFAULT 0,
        snapshot_json NVARCHAR(MAX) NULL,
        snapshot_checksum NVARCHAR(128) NULL,
        pdf_path NVARCHAR(1024) NULL,
        docx_path NVARCHAR(1024) NULL,
        csv_path NVARCHAR(1024) NULL,
        error_message NVARCHAR(MAX) NULL,
        created_by UNIQUEIDENTIFIER NOT NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        started_at DATETIME2 NULL,
        completed_at DATETIME2 NULL,
        updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
    );

    CREATE INDEX ix_evaluation_reports_created_by
        ON evaluation_reports(created_by, created_at DESC);

    CREATE INDEX ix_evaluation_reports_experiments
        ON evaluation_reports(dataset_id, rag_experiment_id, fine_tuned_experiment_id);
END;

GO

-- ============================================================
-- V20260804__resumable_uploads.sql
-- ============================================================
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'upload_sessions')
        CREATE TABLE upload_sessions(
            upload_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
            user_id UNIQUEIDENTIFIER NOT NULL,
            workspace_id UNIQUEIDENTIFIER NULL,
            course_id UNIQUEIDENTIFIER NULL,
            chapter_id UNIQUEIDENTIFIER NULL,
            original_filename NVARCHAR(255) NOT NULL,
            mime_type NVARCHAR(150) NULL,
            total_bytes BIGINT NOT NULL,
            received_bytes BIGINT NOT NULL DEFAULT 0,
            temp_path NVARCHAR(500) NOT NULL,
            status NVARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
            document_id UNIQUEIDENTIFIER NULL,
            created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            row_version BIGINT NOT NULL DEFAULT 0,
            CONSTRAINT chk_upload_sessions_status
                CHECK(status IN('IN_PROGRESS','COMPLETED','ABORTED')),
            CONSTRAINT fk_upload_sessions_user
                FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE CASCADE
        );

    IF COL_LENGTH('upload_sessions', 'row_version') IS NULL
        ALTER TABLE upload_sessions ADD row_version BIGINT NOT NULL DEFAULT 0;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_upload_sessions_user')
        CREATE INDEX ix_upload_sessions_user ON upload_sessions(user_id, status);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

GO

-- ============================================================
-- V20260805__bootstrap_hainam_admin.sql
-- ============================================================
/*
    Bootstrap administrator account for local development.

    The password is stored as a BCrypt hash; plaintext credentials are never
    committed to the repository. Re-running this migration keeps this account
    active and grants it a single active ADMIN role.
*/
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @Email NVARCHAR(255) = N'hainambl996@gmail.com';
    DECLARE @PasswordHash NVARCHAR(100) = N'$2a$10$qYFBBROqKoDqhGwG8ImQ7.FP1G13nzs/CrXRpj4cmTGgIkiHXJsG2';
    DECLARE @UserId UNIQUEIDENTIFIER;

    SELECT @UserId = user_id
    FROM users
    WHERE email = @Email;

    IF @UserId IS NULL
    BEGIN
        INSERT INTO users (full_name, email, password_hash, is_active, created_at, updated_at)
        VALUES (N'Hai Nam', @Email, @PasswordHash, 1, GETDATE(), GETDATE());

        SELECT @UserId = user_id
        FROM users
        WHERE email = @Email;
    END
    ELSE
    BEGIN
        UPDATE users
        SET full_name = N'Hai Nam',
            password_hash = @PasswordHash,
            is_active = 1,
            updated_at = GETDATE()
        WHERE user_id = @UserId;
    END;

    UPDATE user_roles
    SET is_active = 0
    WHERE user_id = @UserId
      AND is_active = 1;

    INSERT INTO user_roles (user_id, role_name, permission_json, assigned_at, is_active)
    VALUES (@UserId, N'ADMIN', N'{"all":true}', GETDATE(), 1);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

GO

-- ============================================================
-- V20260805__vnpay_pro_subscriptions.sql
-- ============================================================
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;

BEGIN TRY
    BEGIN TRANSACTION;

    IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'subscription_plans')
        CREATE TABLE subscription_plans(
            plan_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
            plan_code NVARCHAR(20) NOT NULL,
            display_name NVARCHAR(100) NOT NULL,
            price_vnd BIGINT NOT NULL,
            duration_days INT NULL,
            max_file_bytes BIGINT NOT NULL,
            max_documents INT NOT NULL,
            max_storage_bytes BIGINT NOT NULL,
            max_personal_workspaces INT NOT NULL,
            benefits_json NVARCHAR(MAX) NOT NULL,
            is_active BIT NOT NULL DEFAULT 1,
            created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            CONSTRAINT uq_subscription_plans_code UNIQUE(plan_code),
            CONSTRAINT chk_subscription_plans_price CHECK(price_vnd >= 0),
            CONSTRAINT chk_subscription_plans_duration CHECK(duration_days IS NULL OR duration_days > 0),
            CONSTRAINT chk_subscription_plans_quotas CHECK(
                max_file_bytes > 0 AND max_documents > 0 AND
                max_storage_bytes > 0 AND max_personal_workspaces > 0
            ),
            CONSTRAINT chk_subscription_plans_benefits_json CHECK(ISJSON(benefits_json) = 1)
        );

    IF NOT EXISTS (SELECT 1 FROM subscription_plans WHERE plan_code = 'FREE')
        INSERT INTO subscription_plans(
            plan_code, display_name, price_vnd, duration_days,
            max_file_bytes, max_documents, max_storage_bytes,
            max_personal_workspaces, benefits_json, is_active
        ) VALUES (
            'FREE', N'FREE', 0, NULL,
            10485760, 10, 104857600,
            5, N'["Tối đa 10 MB mỗi file","Tối đa 10 tài liệu","100 MB tổng dung lượng","5 Personal Workspaces"]', 1
        );

    IF NOT EXISTS (SELECT 1 FROM subscription_plans WHERE plan_code = 'PRO')
        INSERT INTO subscription_plans(
            plan_code, display_name, price_vnd, duration_days,
            max_file_bytes, max_documents, max_storage_bytes,
            max_personal_workspaces, benefits_json, is_active
        ) VALUES (
            'PRO', N'PRO', 49000, 30,
            10485760, 50, 524288000,
            10, N'["Tối đa 10 MB mỗi file","Tối đa 50 tài liệu","500 MB tổng dung lượng","10 Personal Workspace"]', 1
        );

    IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'user_subscriptions')
        CREATE TABLE user_subscriptions(
            subscription_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
            user_id UNIQUEIDENTIFIER NOT NULL,
            plan_id UNIQUEIDENTIFIER NOT NULL,
            status NVARCHAR(20) NOT NULL DEFAULT 'FREE',
            started_at DATETIME2 NULL,
            expires_at DATETIME2 NULL,
            updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            row_version BIGINT NOT NULL DEFAULT 0,
            CONSTRAINT uq_user_subscriptions_user UNIQUE(user_id),
            CONSTRAINT chk_user_subscriptions_status CHECK(status IN('FREE','PRO_ACTIVE','PRO_EXPIRED')),
            CONSTRAINT fk_user_subscriptions_user FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE CASCADE,
            CONSTRAINT fk_user_subscriptions_plan FOREIGN KEY(plan_id) REFERENCES subscription_plans(plan_id) ON DELETE NO ACTION
        );

    INSERT INTO user_subscriptions(user_id, plan_id, status)
    SELECT u.user_id, p.plan_id, 'FREE'
    FROM users u
    CROSS JOIN subscription_plans p
    WHERE p.plan_code = 'FREE'
      AND NOT EXISTS (SELECT 1 FROM user_subscriptions s WHERE s.user_id = u.user_id);

    IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'payment_orders')
        CREATE TABLE payment_orders(
            payment_order_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
            vnp_txn_ref NVARCHAR(100) NOT NULL,
            user_id UNIQUEIDENTIFIER NOT NULL,
            plan_id UNIQUEIDENTIFIER NOT NULL,
            plan_code_snapshot NVARCHAR(20) NOT NULL,
            amount_vnd BIGINT NOT NULL,
            duration_days INT NOT NULL,
            gateway NVARCHAR(20) NOT NULL DEFAULT 'VNPAY',
            status NVARCHAR(20) NOT NULL DEFAULT 'PENDING',
            client_ip NVARCHAR(45) NOT NULL,
            expires_at DATETIME2 NOT NULL,
            gateway_transaction_no NVARCHAR(50) NULL,
            gateway_response_code NVARCHAR(10) NULL,
            gateway_transaction_status NVARCHAR(10) NULL,
            bank_code NVARCHAR(20) NULL,
            gateway_pay_date DATETIME2 NULL,
            paid_at DATETIME2 NULL,
            activated_at DATETIME2 NULL,
            created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            row_version BIGINT NOT NULL DEFAULT 0,
            CONSTRAINT uq_payment_orders_txn_ref UNIQUE(vnp_txn_ref),
            CONSTRAINT chk_payment_orders_amount CHECK(amount_vnd > 0),
            CONSTRAINT chk_payment_orders_duration CHECK(duration_days > 0),
            CONSTRAINT chk_payment_orders_gateway CHECK(gateway IN('VNPAY')),
            CONSTRAINT chk_payment_orders_status CHECK(status IN('PENDING','PAID','FAILED','EXPIRED','CANCELLED')),
            CONSTRAINT fk_payment_orders_user FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE NO ACTION,
            CONSTRAINT fk_payment_orders_plan FOREIGN KEY(plan_id) REFERENCES subscription_plans(plan_id) ON DELETE NO ACTION
        );

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_payment_orders_user_created')
        CREATE INDEX ix_payment_orders_user_created ON payment_orders(user_id, created_at DESC);

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_payment_orders_status_expires')
        CREATE INDEX ix_payment_orders_status_expires ON payment_orders(status, expires_at);

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uq_payment_orders_gateway_transaction')
        CREATE UNIQUE INDEX uq_payment_orders_gateway_transaction
            ON payment_orders(gateway_transaction_no)
            WHERE gateway_transaction_no IS NOT NULL;

    IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'subscription_history')
        CREATE TABLE subscription_history(
            subscription_history_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
            user_id UNIQUEIDENTIFIER NOT NULL,
            plan_id UNIQUEIDENTIFIER NOT NULL,
            payment_order_id UNIQUEIDENTIFIER NOT NULL,
            extension_from DATETIME2 NOT NULL,
            extension_to DATETIME2 NOT NULL,
            days_added INT NOT NULL,
            amount_vnd BIGINT NOT NULL,
            paid_at DATETIME2 NOT NULL,
            created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            CONSTRAINT uq_subscription_history_order UNIQUE(payment_order_id),
            CONSTRAINT chk_subscription_history_days CHECK(days_added > 0),
            CONSTRAINT fk_subscription_history_user FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE NO ACTION,
            CONSTRAINT fk_subscription_history_plan FOREIGN KEY(plan_id) REFERENCES subscription_plans(plan_id) ON DELETE NO ACTION,
            CONSTRAINT fk_subscription_history_order FOREIGN KEY(payment_order_id) REFERENCES payment_orders(payment_order_id) ON DELETE NO ACTION
        );

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_subscription_history_user_created')
        CREATE INDEX ix_subscription_history_user_created ON subscription_history(user_id, created_at DESC);

    IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'payment_callback_audits')
        CREATE TABLE payment_callback_audits(
            callback_audit_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
            payment_order_id UNIQUEIDENTIFIER NULL,
            vnp_txn_ref NVARCHAR(100) NULL,
            callback_source NVARCHAR(10) NOT NULL,
            checksum_valid BIT NOT NULL,
            merchant_valid BIT NULL,
            amount_valid BIT NULL,
            order_state_valid BIT NULL,
            validation_error NVARCHAR(100) NULL,
            gateway_transaction_no NVARCHAR(50) NULL,
            gateway_response_code NVARCHAR(10) NULL,
            gateway_transaction_status NVARCHAR(10) NULL,
            payload_json NVARCHAR(MAX) NOT NULL,
            merchant_rsp_code NVARCHAR(10) NULL,
            merchant_message NVARCHAR(255) NULL,
            client_ip NVARCHAR(45) NULL,
            received_at DATETIME2 NOT NULL DEFAULT GETDATE(),
            CONSTRAINT chk_payment_callback_source CHECK(callback_source IN('IPN','RETURN')),
            CONSTRAINT chk_payment_callback_payload_json CHECK(ISJSON(payload_json) = 1),
            CONSTRAINT fk_payment_callback_order FOREIGN KEY(payment_order_id) REFERENCES payment_orders(payment_order_id) ON DELETE NO ACTION
        );

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_payment_callback_order_received')
        CREATE INDEX ix_payment_callback_order_received
            ON payment_callback_audits(payment_order_id, received_at DESC);

    IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uq_personal_workspace_owner')
        DROP INDEX uq_personal_workspace_owner ON course_workspaces;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_personal_workspace_owner')
        CREATE INDEX ix_personal_workspace_owner
            ON course_workspaces(owner_user_id, is_active)
            WHERE course_id IS NULL AND owner_user_id IS NOT NULL AND visibility = 'PRIVATE';

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

GO

-- ============================================================
-- V20260807__chat_message_feedback.sql
-- ============================================================
-- FR-09: Helpful / Not helpful feedback on assistant chat messages

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'chat_message_feedback')
BEGIN
    CREATE TABLE chat_message_feedback (
        feedback_id     UNIQUEIDENTIFIER NOT NULL
                            CONSTRAINT PK_chat_message_feedback PRIMARY KEY,
        message_id      UNIQUEIDENTIFIER NOT NULL,
        user_id         UNIQUEIDENTIFIER NOT NULL,
        helpful         BIT              NOT NULL,
        reason_code     VARCHAR(32)      NULL,
        comment         NVARCHAR(1000)   NULL,
        created_at      DATETIME2        NOT NULL CONSTRAINT DF_cmf_created DEFAULT SYSUTCDATETIME(),
        updated_at      DATETIME2        NOT NULL CONSTRAINT DF_cmf_updated DEFAULT SYSUTCDATETIME(),

        CONSTRAINT FK_cmf_message FOREIGN KEY (message_id)
            REFERENCES chat_messages(message_id) ON DELETE CASCADE,
        CONSTRAINT FK_cmf_user FOREIGN KEY (user_id)
            REFERENCES users(user_id),
        CONSTRAINT UQ_cmf_message_user UNIQUE (message_id, user_id),
        CONSTRAINT CK_cmf_reason CHECK (reason_code IS NULL OR reason_code IN
            ('WRONG_INFORMATION','MISSING_CITATION','OFF_TOPIC','TOO_SLOW','OTHER'))
    );

    CREATE INDEX IX_cmf_message ON chat_message_feedback(message_id);
END
GO

-- ============================================================
-- V20260808__chat_feedback_promotion.sql
-- ============================================================
-- FR-09 loop: let a "not helpful" answer be promoted into an evaluation dataset,
-- so user feedback feeds back into RAG benchmarking instead of only being stored.

IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'chat_message_feedback')
BEGIN
    -- Links the feedback to the evaluation_questions row it produced, so the same
    -- bad answer is not promoted twice and reviewers can see what was acted on.
    IF NOT EXISTS (SELECT 1 FROM sys.columns
                   WHERE object_id = OBJECT_ID('chat_message_feedback')
                     AND name = 'promoted_question_id')
        ALTER TABLE chat_message_feedback
            ADD promoted_question_id UNIQUEIDENTIFIER NULL;

    IF NOT EXISTS (SELECT 1 FROM sys.columns
                   WHERE object_id = OBJECT_ID('chat_message_feedback')
                     AND name = 'promoted_at')
        ALTER TABLE chat_message_feedback
            ADD promoted_at DATETIME2 NULL;
END
GO

-- The insights screen filters on helpful and orders by created_at; without this
-- every stats call is a full scan of the feedback table.
IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'chat_message_feedback')
   AND NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name = 'IX_cmf_helpful_created'
                     AND object_id = OBJECT_ID('chat_message_feedback'))
    CREATE INDEX IX_cmf_helpful_created
        ON chat_message_feedback(helpful, created_at DESC);
GO

-- Feedback is always read per user for a whole session at once.
IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'chat_message_feedback')
   AND NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name = 'IX_cmf_user'
                     AND object_id = OBJECT_ID('chat_message_feedback'))
    CREATE INDEX IX_cmf_user ON chat_message_feedback(user_id);

GO

PRINT 'FStu: schema va tat ca migration da duoc ap dung.';
GO
