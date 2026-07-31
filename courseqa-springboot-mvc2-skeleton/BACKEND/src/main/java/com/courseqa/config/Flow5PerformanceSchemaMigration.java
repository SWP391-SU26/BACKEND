package com.courseqa.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Additive migration for Flow 5 performance telemetry. */
@Component
public class Flow5PerformanceSchemaMigration implements ApplicationRunner {
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public Flow5PerformanceSchemaMigration(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        addIntegerColumnIfMissing("experiment_results", "batch_latency_ms");
        addIntegerColumnIfMissing("experiment_results", "effective_latency_ms");
        addIntegerColumnIfMissing("experiment_results", "batch_size");
        for (String column : new String[] {
                "provider_used", "base_model", "adapter_version", "embedding_model",
                "generation_mode", "dataset_version", "prompt_version", "metric_standard",
                "judge_model", "evaluator_embedding", "model_verification_status"
        }) {
            addTextColumnIfMissing("experiment_results", column);
        }
        addTextColumnIfMissing("evaluation_questions", "expected_source");
        addTextColumnIfMissing("evaluation_questions", "chapter_label");
        addMaxTextColumnIfMissing("evaluation_questions", "evidence_quote");
        addBitColumnIfMissing("evaluation_questions", "is_out_of_scope");
        addNullableBitColumnIfMissing("experiment_results", "source_hit");
        addNullableBitColumnIfMissing("experiment_results", "page_hit");
        addNullableBitColumnIfMissing("experiment_results", "refusal_correct");
        addDoubleColumnIfMissing("experiment_results", "throughput_qps");
        addLongColumnIfMissing("experiment_results", "peak_vram_bytes");
        addNullableBitColumnIfMissing("experiment_results", "quality_gate_passed");
        addTextColumnIfMissing("experiments", "ragas_status");
        addIntegerColumnIfMissing("experiments", "ragas_progress");
        addMaxTextColumnIfMissing("experiments", "ragas_error");
        addDateTimeColumnIfMissing("experiments", "ragas_started_at");
        addDateTimeColumnIfMissing("experiments", "ragas_completed_at");
        addLongColumnIfMissing("experiments", "local_duration_ms");
        addIntegerColumnIfMissing("experiments", "requested_batch_size");
        addIntegerColumnIfMissing("experiments", "effective_batch_size");
        addIntegerColumnIfMissing("experiments", "oom_fallback_count");
        addTextColumnIfMissing("experiment_results", "ragas_status");
        addMaxTextColumnIfMissing("experiment_results", "ragas_error");
        addDateTimeColumnIfMissing("experiment_results", "ragas_evaluated_at");
        jdbcTemplate.update("""
                UPDATE experiments
                SET ragas_status = CASE
                        WHEN status = 'COMPLETED' THEN 'COMPLETED'
                        WHEN status IN ('FAILED', 'CANCELLED') THEN 'FAILED'
                        ELSE 'PENDING'
                    END,
                    ragas_progress = CASE
                        WHEN status IN ('COMPLETED', 'FAILED', 'CANCELLED') THEN 100
                        ELSE 0
                    END
                WHERE ragas_status IS NULL
                """);
        jdbcTemplate.update("""
                UPDATE experiment_results
                SET ragas_status = CASE
                        WHEN metric_standard = 'RAGAS_OFFICIAL' THEN 'COMPLETED'
                        WHEN error_message IS NOT NULL THEN 'FAILED'
                        ELSE 'PENDING'
                    END
                WHERE ragas_status IS NULL
                """);
    }

    private void addIntegerColumnIfMissing(String table, String column) throws SQLException {
        if (hasColumn(table, column)) return;
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD " + column + " INT NULL");
    }

    private void addTextColumnIfMissing(String table, String column) throws SQLException {
        if (hasColumn(table, column)) return;
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD " + column + " NVARCHAR(255) NULL");
    }

    private void addMaxTextColumnIfMissing(String table, String column) throws SQLException {
        if (hasColumn(table, column)) return;
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD " + column + " NVARCHAR(MAX) NULL");
    }

    private void addBitColumnIfMissing(String table, String column) throws SQLException {
        if (hasColumn(table, column)) return;
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD " + column + " BIT NOT NULL DEFAULT 0");
    }

    private void addNullableBitColumnIfMissing(String table, String column) throws SQLException {
        if (hasColumn(table, column)) return;
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD " + column + " BIT NULL");
    }

    private void addDoubleColumnIfMissing(String table, String column) throws SQLException {
        if (hasColumn(table, column)) return;
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD " + column + " FLOAT NULL");
    }

    private void addLongColumnIfMissing(String table, String column) throws SQLException {
        if (hasColumn(table, column)) return;
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD " + column + " BIGINT NULL");
    }

    private void addDateTimeColumnIfMissing(String table, String column) throws SQLException {
        if (hasColumn(table, column)) return;
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD " + column + " DATETIME2 NULL");
    }

    private boolean hasColumn(String table, String column) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            for (String candidate : new String[] {table, table.toUpperCase(), table.toLowerCase()}) {
                try (ResultSet result = metadata.getColumns(connection.getCatalog(), null, candidate, column)) {
                    if (result.next()) return true;
                }
                try (ResultSet result = metadata.getColumns(connection.getCatalog(), null, candidate,
                        column.toUpperCase())) {
                    if (result.next()) return true;
                }
            }
            return false;
        }
    }
}
