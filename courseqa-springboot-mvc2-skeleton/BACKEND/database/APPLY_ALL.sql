/*
    Full database setup, in order.

    VietnameseCourseQA20DB.sql is the original schema and has not been kept in
    sync with later changes, so running it alone produces a database the current
    code cannot use: processing_jobs, upload_sessions, content_hash,
    heading_path, chunk_version and is_active would all be missing, and the
    application (spring.jpa.hibernate.ddl-auto=none) will not create them.

    Every V*.sql below is guarded with IF NOT EXISTS, so re-running this script
    on an existing database is safe.

    Run from this directory:
        sqlcmd -S localhost,1433 -U sa -P <password> -C -i APPLY_ALL.sql
*/

:r VietnameseCourseQA20DB.sql
GO

:r V20260713__semester_workspace_flow.sql
GO
:r V20260715__flow2_security_and_course_scope.sql
GO
:r V20260716__chat_retrieval_scopes.sql
GO
:r V20260717__flow5_dataset_snapshot_and_async_benchmark.sql
GO
:r V20260718__personal_document_library.sql
GO
:r V20260722__offline_multilingual_rag.sql
GO
:r V20260725__chat_workspace_redesign.sql
GO
:r V20260728__cleanup_evaluation_benchmark_chat.sql
GO
:r V20260728__research_model_ragas_metadata.sql
GO
:r V20260729__chat_rag_performance.sql
GO
:r V20260730__rag_quality_and_processing_trace.sql
GO
:r V20260731__parallel_benchmark_and_background_ragas.sql
GO
:r V20260801__document_processing_jobs.sql
GO
:r V20260802__evaluation_report_exports.sql
GO
:r V20260804__resumable_uploads.sql
GO
:r V20260805__bootstrap_hainam_admin.sql
GO
:r V20260805__vnpay_pro_subscriptions.sql
GO

PRINT 'Schema and all migrations applied.';
