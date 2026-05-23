package com.liveklass.assignment.support;

import com.liveklass.assignment.domain.course.InMemoryCourseSeatCounter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@Testcontainers
public abstract class MySqlIntegrationSupport {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("liveklass_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired(required = false)
    private InMemoryCourseSeatCounter seatCounter;

    @BeforeEach
    void cleanDatabase() {
        databaseCleaner.truncate();
        if (seatCounter != null) {
            seatCounter.clearAll();
        }
    }
}
