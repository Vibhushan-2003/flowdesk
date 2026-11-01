package com.flowdesk.common.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Early-phase ApplicationContextInitializer that ensures integration tests target
 * the isolated 'flowdesk_test' database, prevents accidental execution against
 * the active 'flowdesk' development database, and enforces strict database isolation
 * across all environments including local development and CI.
 */
public class TestDatabaseSafetyInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(TestDatabaseSafetyInitializer.class);

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();

        String dbTestUrl = env.getProperty("DB_TEST_URL");
        String effectiveUrl;

        if (dbTestUrl != null && !dbTestUrl.isBlank()) {
            effectiveUrl = dbTestUrl;
            log.info("Test database explicitly configured via DB_TEST_URL: '{}'", effectiveUrl);
        } else {
            // Default to dedicated test database flowdesk_test across all environments
            effectiveUrl = env.getProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/flowdesk_test");
            log.info("Test database target resolved: '{}'", effectiveUrl);
        }

        // Apply high-priority override so all beans (DataSource, Flyway, etc.) use the validated test database URL
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("spring.datasource.url", effectiveUrl);
        env.getPropertySources().addFirst(new MapPropertySource("testDatabaseSafetyOverrides", overrides));

        // Fail-fast validation before any connections, migrations, or queries occur
        TestDatabaseSafetyGuard.validateDatabaseTarget(effectiveUrl);
    }
}
