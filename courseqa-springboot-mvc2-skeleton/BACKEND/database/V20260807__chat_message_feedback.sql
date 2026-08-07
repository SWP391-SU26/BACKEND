-- FR-09: Helpful / Not helpful feedback on assistant chat messages

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'chat_message_feedback')
BEGIN
    CREATE TABLE chat_message_feedback (
        feedback_id     UNIQUEIDENTIFIER NOT NULL
                            CONSTRAINT PK_chat_message_feedback PRIMARY KEY,
        message_id      UNIQUEIDENTIFIER NOT NULL,
        user_id         UNIQUEIDENTIFIER NOT NULL,
        helpful         BIT              NOT NULL,
        reason_code     VARCHAR(32)      NULL,
        comment         NVARCHAR(1000)   NULL,
        created_at      DATETIME2        NOT NULL CONSTRAINT DF_cmf_created DEFAULT SYSUTCDATETIME(),
        updated_at      DATETIME2        NOT NULL CONSTRAINT DF_cmf_updated DEFAULT SYSUTCDATETIME(),

        CONSTRAINT FK_cmf_message FOREIGN KEY (message_id)
            REFERENCES chat_messages(message_id) ON DELETE CASCADE,
        CONSTRAINT FK_cmf_user FOREIGN KEY (user_id)
            REFERENCES users(user_id),
        CONSTRAINT UQ_cmf_message_user UNIQUE (message_id, user_id),
        CONSTRAINT CK_cmf_reason CHECK (reason_code IS NULL OR reason_code IN
            ('WRONG_INFORMATION','MISSING_CITATION','OFF_TOPIC','TOO_SLOW','OTHER'))
    );

    CREATE INDEX IX_cmf_message ON chat_message_feedback(message_id);
END