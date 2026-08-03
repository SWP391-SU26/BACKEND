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
