package com.flowdesk.common.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Startup safety guard verifying that automated integration tests connect strictly
 * to the designated isolated test database ('flowdesk_test') across all environments,
 * and never to the active development database ('flowdesk').
 */
@Component
public class TestDatabaseSafetyGuard implements BeanPostProcessor, PriorityOrdered, SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(TestDatabaseSafetyGuard.class);

    public static final String BLOCKED_DATABASE_NAME = "flowdesk";
    public static final String DEFAULT_TEST_DATABASE_NAME = "flowdesk_test";

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired(required = false)
    private Environment environment;

    @Override
    public int getOrder() {
        return PriorityOrdered.HIGHEST_PRECEDENCE;
    }

    /**
     * Intercepts DataSource bean instantiation immediately upon creation, BEFORE
     * Flyway migrations or JPA/Hibernate schema validations execute.
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource ds) {
            log.info("Intercepted DataSource initialization for bean '{}'. Validating live database identity...", beanName);
            validateLiveDataSource(ds);
        }
        return bean;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (dataSource != null) {
            validateLiveDataSource(dataSource);
        }
    }

    /**
     * Inspects a live DataSource by querying 'SELECT current_database(), current_user'
     * and verifying that the target database identity is an approved test database.
     */
    public static void validateLiveDataSource(DataSource ds) {
        if (ds == null) {
            return;
        }

        try (Connection conn = ds.getConnection()) {
            String currentDatabase = null;
            String currentUser = null;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT current_database(), current_user")) {
                if (rs.next()) {
                    currentDatabase = rs.getString(1);
                    currentUser = rs.getString(2);
                }
            }

            if (currentDatabase == null || currentDatabase.isBlank()) {
                currentDatabase = conn.getCatalog();
            }

            log.info("Live test database connection confirmed: database='{}', user='{}'", currentDatabase, currentUser);
            validateDatabaseName(currentDatabase);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to verify live database identity during test safety validation: " + e.getMessage(), e);
        }
    }

    /**
     * Validates that the database name is an approved test database and strictly NOT the development database.
     */
    public static void validateDatabaseName(String dbName) {
        if (dbName == null || dbName.isBlank()) {
            throw new IllegalStateException("Database name could not be determined for test execution.");
        }

        String normalized = dbName.trim().toLowerCase();

        if (BLOCKED_DATABASE_NAME.equalsIgnoreCase(normalized)) {
            throw new IllegalStateException(
                    "CRITICAL TEST DATA SAFETY VIOLATION: Automated tests attempted to connect to the active development database '" +
                    BLOCKED_DATABASE_NAME + "'! " +
                    "Integration tests are strictly forbidden from connecting to '" + BLOCKED_DATABASE_NAME + "' " +
                    "to prevent accidental deletion or modification of development and browser testing data. " +
                    "Tests must execute against the designated test database ('" + DEFAULT_TEST_DATABASE_NAME + "')."
            );
        }

        if (!normalized.contains("test")) {
            throw new IllegalStateException(
                    "TEST DATABASE SAFETY VIOLATION: Database '" + dbName + "' is not recognized as a designated test database. " +
                    "Expected a database name containing 'test' (e.g., '" + DEFAULT_TEST_DATABASE_NAME + "')."
            );
        }
    }

    /**
     * Extracts database name from JDBC URL string.
     */
    public static String extractDatabaseName(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "";
        }
        String cleanUrl = jdbcUrl;
        int questionMark = cleanUrl.indexOf('?');
        if (questionMark >= 0) {
            cleanUrl = cleanUrl.substring(0, questionMark);
        }
        int lastSlash = cleanUrl.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash + 1 < cleanUrl.length()) {
            return cleanUrl.substring(lastSlash + 1).trim();
        }
        return "";
    }

    /**
     * Validates the database extracted from a JDBC URL.
     */
    public static void validateDatabaseTarget(String jdbcUrl) {
        String dbName = extractDatabaseName(jdbcUrl);
        validateDatabaseName(dbName);
    }
}
