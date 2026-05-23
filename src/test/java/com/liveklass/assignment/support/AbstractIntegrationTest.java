package com.liveklass.assignment.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({DatabaseCleaner.class, TestClockConfig.class})
public abstract class AbstractIntegrationTest extends MySqlIntegrationSupport {
}
