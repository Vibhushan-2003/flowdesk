package com.flowdesk.user.bootstrap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminBootstrapActivationTest {

    @Test
    @DisplayName("Should detect CLI option --flowdesk.bootstrap.admin")
    void detectCliOption() {
        String[] args = new String[]{"--flowdesk.bootstrap.admin"};
        assertTrue(AdminBootstrapActivation.isBootstrapRequested(args));
        assertTrue(AdminBootstrapActivation.isBootstrapRequested(args, null));
    }

    @Test
    @DisplayName("Should detect CLI option --flowdesk.bootstrap.admin=true")
    void detectCliOptionWithValue() {
        String[] args = new String[]{"--flowdesk.bootstrap.admin=true"};
        assertTrue(AdminBootstrapActivation.isBootstrapRequested(args));
    }

    @Test
    @DisplayName("Should detect CLI option --app.bootstrap.admin.enabled=true")
    void detectCliPropertyOption() {
        String[] args = new String[]{"--app.bootstrap.admin.enabled=true"};
        assertTrue(AdminBootstrapActivation.isBootstrapRequested(args));
    }

    @Test
    @DisplayName("Should return false when args do not contain bootstrap options")
    void falseWhenNoArgs() {
        assertFalse(AdminBootstrapActivation.isBootstrapRequested(new String[]{}));
        assertFalse(AdminBootstrapActivation.isBootstrapRequested(new String[]{"--server.port=8080"}));
    }

    @Test
    @DisplayName("Should detect environment variable or property when args are empty")
    void detectEnvironmentProperty() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.bootstrap.admin.enabled", "true");

        assertTrue(AdminBootstrapActivation.isBootstrapRequested(new String[]{}, env));
    }

    @Test
    @DisplayName("Should return false when environment property is false or missing")
    void falseWhenEnvironmentDisabled() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.bootstrap.admin.enabled", "false");

        assertFalse(AdminBootstrapActivation.isBootstrapRequested(new String[]{}, env));
    }
}
