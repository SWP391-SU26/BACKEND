-- FR-09 loop: let a "not helpful" answer be promoted into an evaluation dataset,
-- so user feedback feeds back into RAG benchmarking instead of only being stored.

IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'chat_message_feedback')
BEGIN
    -- Links the feedback to the evaluation_questions row it produced, so the same
    -- bad answer is not promoted twice and reviewers can see what was acted on.
    IF NOT EXISTS (SELECT 1 FROM sys.columns
                   WHERE object_id = OBJECT_ID('chat_message_feedback')
                     AND name = 'promoted_question_id')
        ALTER TABLE chat_message_feedback
            ADD promoted_question_id UNIQUEIDENTIFIER NULL;

    IF NOT EXISTS (SELECT 1 FROM sys.columns
                   WHERE object_id = OBJECT_ID('chat_message_feedback')
                     AND name = 'promoted_at')
        ALTER TABLE chat_message_feedback
            ADD promoted_at DATETIME2 NULL;
END
GO

-- The insights screen filters on helpful and orders by created_at; without this
-- every stats call is a full scan of the feedback table.
IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'chat_message_feedback')
   AND NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name = 'IX_cmf_helpful_created'
                     AND object_id = OBJECT_ID('chat_message_feedback'))
    CREATE INDEX IX_cmf_helpful_created
        ON chat_message_feedback(helpful, created_at DESC);
GO

-- Feedback is always read per user for a whole session at once.
IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'chat_message_feedback')
   AND NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE name = 'IX_cmf_user'
                     AND object_id = OBJECT_ID('chat_message_feedback'))
    CREATE INDEX IX_cmf_user ON chat_message_feedback(user_id);
