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
