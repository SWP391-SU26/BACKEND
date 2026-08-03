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
