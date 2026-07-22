SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH('dbo.course_documents', 'deleted_at') IS NULL ALTER TABLE dbo.course_documents ADD deleted_at DATETIME2 NULL;
IF COL_LENGTH('dbo.course_documents', 'deleted_by') IS NULL ALTER TABLE dbo.course_documents ADD deleted_by UNIQUEIDENTIFIER NULL;
IF COL_LENGTH('dbo.course_documents', 'deleted_scope') IS NULL ALTER TABLE dbo.course_documents ADD deleted_scope NVARCHAR(20) NULL;
IF COL_LENGTH('dbo.course_documents', 'deleted_review_status') IS NULL ALTER TABLE dbo.course_documents ADD deleted_review_status NVARCHAR(20) NULL;
IF COL_LENGTH('dbo.course_documents', 'deleted_course_id') IS NULL ALTER TABLE dbo.course_documents ADD deleted_course_id UNIQUEIDENTIFIER NULL;
IF COL_LENGTH('dbo.course_documents', 'deleted_workspace_id') IS NULL ALTER TABLE dbo.course_documents ADD deleted_workspace_id UNIQUEIDENTIFIER NULL;

IF COL_LENGTH('dbo.courses', 'deleted_at') IS NULL ALTER TABLE dbo.courses ADD deleted_at DATETIME2 NULL;
IF COL_LENGTH('dbo.courses', 'deleted_by') IS NULL ALTER TABLE dbo.courses ADD deleted_by UNIQUEIDENTIFIER NULL;
IF COL_LENGTH('dbo.semester_workspaces', 'deleted_at') IS NULL ALTER TABLE dbo.semester_workspaces ADD deleted_at DATETIME2 NULL;
IF COL_LENGTH('dbo.semester_workspaces', 'deleted_by') IS NULL ALTER TABLE dbo.semester_workspaces ADD deleted_by UNIQUEIDENTIFIER NULL;

IF COL_LENGTH('dbo.chat_sessions', 'is_pinned') IS NULL ALTER TABLE dbo.chat_sessions ADD is_pinned BIT NOT NULL CONSTRAINT df_chat_sessions_is_pinned DEFAULT 0;
IF COL_LENGTH('dbo.chat_sessions', 'pinned_at') IS NULL ALTER TABLE dbo.chat_sessions ADD pinned_at DATETIME2 NULL;

GO

UPDATE dbo.courses
SET deleted_at = COALESCE(deleted_at, updated_at, GETDATE()),
    deleted_by = COALESCE(deleted_by, created_by),
    is_active = 0
WHERE status = 'ARCHIVED' AND deleted_at IS NULL;

UPDATE dbo.semester_workspaces
SET deleted_at = COALESCE(deleted_at, updated_at, GETDATE()),
    deleted_by = COALESCE(deleted_by, created_by)
WHERE status = 'ARCHIVED' AND deleted_at IS NULL;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_course_documents_deleted_at' AND object_id = OBJECT_ID('dbo.course_documents'))
    CREATE INDEX ix_course_documents_deleted_at ON dbo.course_documents(deleted_at, uploaded_by);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_courses_deleted_at' AND object_id = OBJECT_ID('dbo.courses'))
    CREATE INDEX ix_courses_deleted_at ON dbo.courses(deleted_at, semester_workspace_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_semester_workspaces_deleted_at' AND object_id = OBJECT_ID('dbo.semester_workspaces'))
    CREATE INDEX ix_semester_workspaces_deleted_at ON dbo.semester_workspaces(deleted_at);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_chat_sessions_pinned' AND object_id = OBJECT_ID('dbo.chat_sessions'))
    CREATE INDEX ix_chat_sessions_pinned ON dbo.chat_sessions(user_id, is_pinned, pinned_at, updated_at);

COMMIT TRANSACTION;
