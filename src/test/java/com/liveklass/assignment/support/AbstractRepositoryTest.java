package com.liveklass.assignment.support;

import com.liveklass.assignment.common.persistence.JpaConfig;
import javax.sql.DataSource;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DatabaseCleaner.class, JpaConfig.class, AbstractRepositoryTest.JdbcTemplateConfig.class})
public abstract class AbstractRepositoryTest extends MySqlIntegrationSupport {

    @TestConfiguration
    static class JdbcTemplateConfig {
        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
