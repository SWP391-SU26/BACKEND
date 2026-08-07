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
