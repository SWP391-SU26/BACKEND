IF COL_LENGTH('experiment_results', 'provider_used') IS NULL
    ALTER TABLE experiment_results ADD provider_used NVARCHAR(100) NULL;
IF COL_LENGTH('experiment_results', 'base_model') IS NULL
    ALTER TABLE experiment_results ADD base_model NVARCHAR(255) NULL;
IF COL_LENGTH('experiment_results', 'adapter_version') IS NULL
    ALTER TABLE experiment_results ADD adapter_version NVARCHAR(255) NULL;
IF COL_LENGTH('experiment_results', 'embedding_model') IS NULL
    ALTER TABLE experiment_results ADD embedding_model NVARCHAR(255) NULL;
IF COL_LENGTH('experiment_results', 'generation_mode') IS NULL
    ALTER TABLE experiment_results ADD generation_mode NVARCHAR(50) NULL;
IF COL_LENGTH('experiment_results', 'dataset_version') IS NULL
    ALTER TABLE experiment_results ADD dataset_version NVARCHAR(100) NULL;
IF COL_LENGTH('experiment_results', 'prompt_version') IS NULL
    ALTER TABLE experiment_results ADD prompt_version NVARCHAR(100) NULL;
IF COL_LENGTH('experiment_results', 'metric_standard') IS NULL
    ALTER TABLE experiment_results ADD metric_standard NVARCHAR(50) NULL;
IF COL_LENGTH('experiment_results', 'judge_model') IS NULL
    ALTER TABLE experiment_results ADD judge_model NVARCHAR(255) NULL;
IF COL_LENGTH('experiment_results', 'evaluator_embedding') IS NULL
    ALTER TABLE experiment_results ADD evaluator_embedding NVARCHAR(255) NULL;
IF COL_LENGTH('evaluation_questions', 'expected_source') IS NULL
    ALTER TABLE evaluation_questions ADD expected_source NVARCHAR(500) NULL;
IF COL_LENGTH('evaluation_questions', 'evidence_quote') IS NULL
    ALTER TABLE evaluation_questions ADD evidence_quote NVARCHAR(MAX) NULL;
IF COL_LENGTH('evaluation_questions', 'chapter_label') IS NULL
    ALTER TABLE evaluation_questions ADD chapter_label NVARCHAR(255) NULL;
IF COL_LENGTH('evaluation_questions', 'is_out_of_scope') IS NULL
    ALTER TABLE evaluation_questions ADD is_out_of_scope BIT NOT NULL
        CONSTRAINT DF_evaluation_questions_is_out_of_scope DEFAULT 0;
IF COL_LENGTH('experiment_results', 'source_hit') IS NULL
    ALTER TABLE experiment_results ADD source_hit BIT NULL;
IF COL_LENGTH('experiment_results', 'page_hit') IS NULL
    ALTER TABLE experiment_results ADD page_hit BIT NULL;
IF COL_LENGTH('experiment_results', 'refusal_correct') IS NULL
    ALTER TABLE experiment_results ADD refusal_correct BIT NULL;
IF COL_LENGTH('experiment_results', 'throughput_qps') IS NULL
    ALTER TABLE experiment_results ADD throughput_qps FLOAT NULL;
IF COL_LENGTH('experiment_results', 'peak_vram_bytes') IS NULL
    ALTER TABLE experiment_results ADD peak_vram_bytes BIGINT NULL;
IF COL_LENGTH('experiment_results', 'model_verification_status') IS NULL
    ALTER TABLE experiment_results ADD model_verification_status NVARCHAR(50) NULL;
IF COL_LENGTH('experiment_results', 'quality_gate_passed') IS NULL
    ALTER TABLE experiment_results ADD quality_gate_passed BIT NULL;
