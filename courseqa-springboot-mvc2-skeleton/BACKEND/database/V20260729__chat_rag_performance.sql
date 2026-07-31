SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    ;WITH ranked_embeddings AS (
        SELECT
            chunk_embedding_id,
            ROW_NUMBER() OVER (
                PARTITION BY embedding_model_id, chunk_id
                ORDER BY created_at DESC, chunk_embedding_id DESC
            ) AS duplicate_rank
        FROM chunk_embeddings
    )
    DELETE FROM ranked_embeddings
    WHERE duplicate_rank > 1;

    IF COL_LENGTH('chunk_embeddings', 'embedding_compressed') IS NULL
        ALTER TABLE chunk_embeddings ADD embedding_compressed VARBINARY(MAX) NULL;

    EXEC sys.sp_executesql N'
        UPDATE chunk_embeddings
        SET embedding_compressed = COMPRESS(CONVERT(VARCHAR(MAX), embedding_json))
        WHERE embedding_compressed IS NULL
          AND embedding_json IS NOT NULL;
    ';

    IF COL_LENGTH('document_chunks', 'content_compressed') IS NULL
        ALTER TABLE document_chunks ADD content_compressed VARBINARY(MAX) NULL;

    EXEC sys.sp_executesql N'
        UPDATE document_chunks
        SET content_compressed = COMPRESS(CONVERT(VARBINARY(MAX), content))
        WHERE content IS NOT NULL;
    ';

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE name = 'ux_chunk_embeddings_model_chunk'
          AND object_id = OBJECT_ID('chunk_embeddings')
    )
        CREATE UNIQUE INDEX ux_chunk_embeddings_model_chunk
            ON chunk_embeddings(embedding_model_id, chunk_id);

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE name = 'ix_document_chunks_document_created'
          AND object_id = OBJECT_ID('document_chunks')
    )
        CREATE INDEX ix_document_chunks_document_created
            ON document_chunks(document_id, created_at)
            INCLUDE (chunk_id, workspace_id, course_id, chunk_index, page_start, page_end);

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE name = 'ix_document_chunks_workspace_created'
          AND object_id = OBJECT_ID('document_chunks')
    )
        CREATE INDEX ix_document_chunks_workspace_created
            ON document_chunks(workspace_id, created_at)
            INCLUDE (chunk_id, document_id, course_id, chunk_index, page_start, page_end);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
