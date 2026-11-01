package com.flowdesk.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Production and Development Configuration Tests")
class ProductionConfigurationTest {

    @Configuration
    static class EmptyTestConfiguration {
    }

    private ConfigurableApplicationContext createContext(String profile, String... extraProperties) {
        String originalProfile = System.getProperty("spring.profiles.active");
        try {
            if (profile != null) {
                System.setProperty("spring.profiles.active", profile);
            } else {
                System.clearProperty("spring.profiles.active");
            }

            SpringApplicationBuilder builder = new SpringApplicationBuilder(EmptyTestConfiguration.class)
                    .web(WebApplicationType.NONE)
                    .properties(
                            "DB_TEST_URL=jdbc:postgresql://localhost:5432/flowdesk_test",
                            "spring.datasource.url=jdbc:postgresql://localhost:5432/flowdesk_test",
                            "DB_USERNAME=flowdesk_app",
                            "DB_PASSWORD=secret",
                            "JWT_SECRET=dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLTEyMzQ1Ng=="
                    );
            if (extraProperties != null && extraProperties.length > 0) {
                builder.properties(extraProperties);
            }
            return builder.run();
        } finally {
            if (originalProfile != null) {
                System.setProperty("spring.profiles.active", originalProfile);
            } else {
                System.clearProperty("spring.profiles.active");
            }
        }
    }

    @Test
    @DisplayName("Under 'prod' profile, verbose SQL printing must be disabled by default")
    void shouldDisableSqlPrintingUnderProductionProfileByDefault() {
        try (ConfigurableApplicationContext context = createContext("prod")) {
            Environment env = context.getEnvironment();
            assertThat(env.getProperty("spring.jpa.show-sql")).isEqualTo("false");
        }
    }

    @Test
    @DisplayName("Under 'prod' profile, developer can explicitly override SQL printing for targeted debugging")
    void shouldAllowDeveloperOverrideOfSqlPrintingUnderProdProfile() {
        try (ConfigurableApplicationContext context = createContext("prod", "SPRING_JPA_SHOW_SQL=true")) {
            Environment env = context.getEnvironment();
            assertThat(env.getProperty("spring.jpa.show-sql")).isEqualTo("true");
        }
    }

    @Test
    @DisplayName("In default development mode (no profile), SQL printing defaults to enabled for localhost debugging")
    void shouldEnableSqlPrintingInDefaultDevelopmentMode() {
        try (ConfigurableApplicationContext context = createContext("default")) {
            Environment env = context.getEnvironment();
            assertThat(env.getProperty("spring.jpa.show-sql")).isEqualTo("true");
        }
    }

    @Test
    @DisplayName("Under 'prod' profile, production safety flags and error masking must be active")
    void shouldEnforceProductionFlagsUnderProdProfile() {
        try (ConfigurableApplicationContext context = createContext("prod")) {
            Environment env = context.getEnvironment();
            assertThat(env.getProperty("app.mail.production")).isEqualTo("true");
            assertThat(env.getProperty("app.security.password-reset.production")).isEqualTo("true");
            assertThat(env.getProperty("app.security.invitation.production")).isEqualTo("true");
            assertThat(env.getProperty("server.error.include-stacktrace")).isEqualTo("never");
            assertThat(env.getProperty("server.error.include-message")).isEqualTo("never");
            assertThat(env.getProperty("server.error.include-exception")).isEqualTo("false");
            assertThat(env.getProperty("logging.level.org.hibernate.SQL")).isEqualTo("WARN");
            assertThat(env.getProperty("logging.level.org.hibernate.orm.jdbc.bind")).isEqualTo("WARN");
            assertThat(env.getProperty("logging.level.org.hibernate.type.descriptor.sql")).isEqualTo("WARN");
        }
    }

    @Test
    @DisplayName("Under 'prod' profile, HikariCP connection pool defaults must be configured with standard conservative values")
    void shouldConfigureHikariPoolDefaults() {
        try (ConfigurableApplicationContext context = createContext("prod")) {
            Environment env = context.getEnvironment();
            assertThat(env.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("10");
            assertThat(env.getProperty("spring.datasource.hikari.minimum-idle")).isEqualTo("5");
            assertThat(env.getProperty("spring.datasource.hikari.connection-timeout")).isEqualTo("30000");
            assertThat(env.getProperty("spring.datasource.hikari.idle-timeout")).isEqualTo("600000");
            assertThat(env.getProperty("spring.datasource.hikari.max-lifetime")).isEqualTo("1800000");
        }
    }

    @Test
    @DisplayName("Under 'test' profile, SQL printing must default to disabled to keep test logs clean")
    void shouldSuppressSqlPrintingInTestProfile() {
        try (ConfigurableApplicationContext context = createContext("test")) {
            Environment env = context.getEnvironment();
            assertThat(env.getProperty("spring.jpa.show-sql")).isEqualTo("false");
        }
    }
}
