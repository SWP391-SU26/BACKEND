SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH('course_documents', 'indexing_status') IS NULL
    ALTER TABLE course_documents ADD indexing_status VARCHAR(20) NULL;
IF COL_LENGTH('course_documents', 'indexed_embedding_model_id') IS NULL
    ALTER TABLE course_documents ADD indexed_embedding_model_id UNIQUEIDENTIFIER NULL;
IF COL_LENGTH('course_documents', 'indexed_model_version') IS NULL
    ALTER TABLE course_documents ADD indexed_model_version NVARCHAR(255) NULL;
IF COL_LENGTH('course_documents', 'indexed_at') IS NULL
    ALTER TABLE course_documents ADD indexed_at DATETIME2 NULL;
IF COL_LENGTH('course_documents', 'index_error') IS NULL
    ALTER TABLE course_documents ADD index_error NVARCHAR(MAX) NULL;

IF COL_LENGTH('experiments', 'similarity_threshold') IS NULL
    ALTER TABLE experiments ADD similarity_threshold FLOAT NULL;
IF COL_LENGTH('experiments', 'embedding_model_version') IS NULL
    ALTER TABLE experiments ADD embedding_model_version NVARCHAR(255) NULL;
IF COL_LENGTH('experiments', 'generation_model_version') IS NULL
    ALTER TABLE experiments ADD generation_model_version NVARCHAR(255) NULL;
IF COL_LENGTH('experiments', 'random_seed') IS NULL
    ALTER TABLE experiments ADD random_seed INT NULL;
IF COL_LENGTH('experiments', 'metric_standard') IS NULL
    ALTER TABLE experiments ADD metric_standard VARCHAR(30) NULL;
IF COL_LENGTH('experiments', 'evaluator_config_json') IS NULL
    ALTER TABLE experiments ADD evaluator_config_json NVARCHAR(MAX) NULL;
IF COL_LENGTH('experiments', 'frozen_config_hash') IS NULL
    ALTER TABLE experiments ADD frozen_config_hash VARCHAR(64) NULL;

IF COL_LENGTH('experiment_results', 'ragas_status') IS NULL
    ALTER TABLE experiment_results ADD ragas_status VARCHAR(30) NULL;
IF COL_LENGTH('experiment_results', 'ragas_error') IS NULL
    ALTER TABLE experiment_results ADD ragas_error NVARCHAR(MAX) NULL;
IF COL_LENGTH('experiment_results', 'metric_standard') IS NULL
    ALTER TABLE experiment_results ADD metric_standard VARCHAR(30) NULL;
IF COL_LENGTH('experiment_results', 'evaluator_model') IS NULL
    ALTER TABLE experiment_results ADD evaluator_model NVARCHAR(255) NULL;
IF COL_LENGTH('experiment_results', 'evaluator_embedding_model') IS NULL
    ALTER TABLE experiment_results ADD evaluator_embedding_model NVARCHAR(255) NULL;
IF COL_LENGTH('experiment_results', 'ragas_version') IS NULL
    ALTER TABLE experiment_results ADD ragas_version VARCHAR(30) NULL;

IF OBJECT_ID('experiment_metric_aggregates', 'U') IS NULL
BEGIN
    CREATE TABLE experiment_metric_aggregates (
        aggregate_id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
        experiment_id UNIQUEIDENTIFIER NOT NULL,
        metric_name VARCHAR(60) NOT NULL,
        average_value FLOAT NULL,
        min_value FLOAT NULL,
        max_value FLOAT NULL,
        standard_deviation FLOAT NULL,
        sample_count INT NOT NULL DEFAULT 0,
        failure_count INT NOT NULL DEFAULT 0,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT uq_experiment_metric_aggregate UNIQUE (experiment_id, metric_name)
    );
END;

-- SQL Server compiles a batch before executing ALTER TABLE statements. Start a
-- new batch so the backfill below can resolve the newly added columns.
GO

UPDATE experiments
SET metric_standard = COALESCE(metric_standard, 'LOCAL_PROXY'),
    similarity_threshold = COALESCE(similarity_threshold, 0.25),
    random_seed = COALESCE(random_seed, 42)
WHERE metric_standard IS NULL OR similarity_threshold IS NULL OR random_seed IS NULL;

UPDATE experiment_results
SET metric_standard = COALESCE(metric_standard, 'LOCAL_PROXY'),
    ragas_status = COALESCE(ragas_status, 'NOT_EVALUATED')
WHERE metric_standard IS NULL OR ragas_status IS NULL;

UPDATE course_documents SET indexing_status = 'PENDING' WHERE indexing_status IS NULL;

;WITH bge AS (
    SELECT TOP 1 embedding_model_id, model_name
    FROM embedding_models
    WHERE LOWER(model_name) = LOWER('BAAI/bge-m3')
    ORDER BY created_at DESC
), indexed_documents AS (
    SELECT c.document_id, b.embedding_model_id, COUNT(*) AS chunk_count,
           SUM(CASE WHEN e.chunk_embedding_id IS NOT NULL THEN 1 ELSE 0 END) AS embedding_count
    FROM document_chunks c
    CROSS JOIN bge b
    LEFT JOIN chunk_embeddings e
      ON e.chunk_id = c.chunk_id AND e.embedding_model_id = b.embedding_model_id
    GROUP BY c.document_id, b.embedding_model_id
)
UPDATE d
SET d.indexing_status = CASE WHEN x.chunk_count > 0 AND x.chunk_count = x.embedding_count THEN 'INDEXED' ELSE 'PENDING' END,
    d.indexed_embedding_model_id = CASE WHEN x.chunk_count = x.embedding_count THEN x.embedding_model_id ELSE NULL END,
    d.indexed_model_version = CASE WHEN x.chunk_count = x.embedding_count THEN 'legacy-import' ELSE NULL END,
    d.indexed_at = CASE WHEN x.chunk_count = x.embedding_count THEN COALESCE(d.updated_at, d.uploaded_at) ELSE NULL END
FROM course_documents d
JOIN indexed_documents x ON x.document_id = d.document_id
WHERE d.processing_status = 'PROCESSED';

COMMIT TRANSACTION;
