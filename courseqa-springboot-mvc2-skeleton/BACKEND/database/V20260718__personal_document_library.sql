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
