SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;

IF COL_LENGTH('chat_sessions', 'is_pinned') IS NULL
    ALTER TABLE chat_sessions ADD is_pinned BIT NOT NULL
        CONSTRAINT df_chat_sessions_is_pinned DEFAULT 0;

IF COL_LENGTH('chat_sessions', 'pinned_at') IS NULL
    ALTER TABLE chat_sessions ADD pinned_at DATETIME2 NULL;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'ix_chat_sessions_owner_history'
      AND object_id = OBJECT_ID('chat_sessions')
)
    CREATE INDEX ix_chat_sessions_owner_history
        ON chat_sessions(user_id, is_active, is_pinned, pinned_at, updated_at);
