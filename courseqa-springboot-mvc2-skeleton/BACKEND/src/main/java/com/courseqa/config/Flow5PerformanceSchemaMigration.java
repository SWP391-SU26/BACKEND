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
    }

    private void addIntegerColumnIfMissing(String table, String column) throws SQLException {
        if (hasColumn(table, column)) return;
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD " + column + " INT NULL");
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
