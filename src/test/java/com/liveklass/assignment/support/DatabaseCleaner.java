package com.liveklass.assignment.support;

import java.util.List;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

@TestComponent
public class DatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void truncate() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            List<String> tables = jdbcTemplate.queryForList("""
                    SELECT table_name
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_type = 'BASE TABLE'
                      AND table_name <> 'flyway_schema_history'
                    """, String.class);
            tables.forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE `" + table + "`"));
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }
}
