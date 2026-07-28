SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    SELECT chat_session_id
    INTO #BenchmarkSessions
    FROM chat_sessions
    WHERE session_title = N'Evaluation benchmark';

    SELECT message_id
    INTO #BenchmarkMessages
    FROM chat_messages
    WHERE chat_session_id IN (SELECT chat_session_id FROM #BenchmarkSessions);

    SELECT
        (SELECT COUNT(*) FROM #BenchmarkSessions) AS sessions_before,
        (SELECT COUNT(*) FROM #BenchmarkMessages) AS messages_before;

    DELETE FROM answer_citations
    WHERE assistant_message_id IN (SELECT message_id FROM #BenchmarkMessages)
       OR retrieval_result_id IN (
            SELECT rr.retrieval_result_id
            FROM retrieval_results rr
            JOIN retrieval_queries rq
              ON rq.retrieval_query_id = rr.retrieval_query_id
            WHERE rq.chat_session_id IN (SELECT chat_session_id FROM #BenchmarkSessions)
       );

    DELETE FROM retrieval_results
    WHERE retrieval_query_id IN (
        SELECT retrieval_query_id
        FROM retrieval_queries
        WHERE chat_session_id IN (SELECT chat_session_id FROM #BenchmarkSessions)
    );

    DELETE FROM retrieval_queries
    WHERE chat_session_id IN (SELECT chat_session_id FROM #BenchmarkSessions);

    DELETE FROM chat_messages
    WHERE chat_session_id IN (SELECT chat_session_id FROM #BenchmarkSessions);

    DELETE FROM chat_session_documents
    WHERE chat_session_id IN (SELECT chat_session_id FROM #BenchmarkSessions);

    DELETE FROM chat_sessions
    WHERE chat_session_id IN (SELECT chat_session_id FROM #BenchmarkSessions);

    SELECT
        (SELECT COUNT(*) FROM chat_sessions WHERE session_title = N'Evaluation benchmark')
            AS sessions_after,
        (SELECT COUNT(*)
         FROM chat_messages message
         JOIN chat_sessions session
           ON session.chat_session_id = message.chat_session_id
         WHERE session.session_title = N'Evaluation benchmark')
            AS messages_after;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
