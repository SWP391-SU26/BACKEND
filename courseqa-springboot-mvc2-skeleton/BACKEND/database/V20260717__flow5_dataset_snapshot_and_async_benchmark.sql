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
