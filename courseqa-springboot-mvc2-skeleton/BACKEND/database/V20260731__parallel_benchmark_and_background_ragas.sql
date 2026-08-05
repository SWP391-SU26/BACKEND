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
