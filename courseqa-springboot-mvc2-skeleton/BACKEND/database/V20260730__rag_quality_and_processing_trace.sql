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
