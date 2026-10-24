package com.flowdesk.user.bootstrap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminBootstrapPropertiesTest {

    @Test
    @DisplayName("Should be disabled by default with null credentials")
    void defaultValues() {
        AdminBootstrapProperties props = new AdminBootstrapProperties();

        assertFalse(props.isEnabled());
        assertDoesNotThrow(props::validate, "Validation should pass without credentials when disabled");
    }

    @Test
    @DisplayName("Should fail validation when enabled but email is missing")
    void enabledMissingEmail() {
        AdminBootstrapProperties props = new AdminBootstrapProperties();
        props.setEnabled(true);
        props.setPassword("StrongAdminPass123!");

        AdminBootstrapException ex = assertThrows(AdminBootstrapException.class, props::validate);
        assertEquals("Bootstrap admin email is required when bootstrap is enabled", ex.getMessage());
    }

    @Test
    @DisplayName("Should fail validation when enabled but email is invalid")
    void enabledInvalidEmail() {
        AdminBootstrapProperties props = new AdminBootstrapProperties();
        props.setEnabled(true);
        props.setEmail("not-an-email");
        props.setPassword("StrongAdminPass123!");

        AdminBootstrapException ex = assertThrows(AdminBootstrapException.class, props::validate);
        assertEquals("Bootstrap admin email is invalid", ex.getMessage());
    }

    @Test
    @DisplayName("Should fail validation when enabled but password is blank")
    void enabledMissingPassword() {
        AdminBootstrapProperties props = new AdminBootstrapProperties();
        props.setEnabled(true);
        props.setEmail("admin@flowdesk.test");
        props.setPassword("   ");

        AdminBootstrapException ex = assertThrows(AdminBootstrapException.class, props::validate);
        assertEquals("Bootstrap admin password is required when bootstrap is enabled", ex.getMessage());
    }

    @Test
    @DisplayName("Should fail validation when password is too short")
    void enabledShortPassword() {
        AdminBootstrapProperties props = new AdminBootstrapProperties();
        props.setEnabled(true);
        props.setEmail("admin@flowdesk.test");
        props.setPassword("short1");

        AdminBootstrapException ex = assertThrows(AdminBootstrapException.class, props::validate);
        assertTrue(ex.getMessage().contains("must be between 8 and 72 characters"));
    }

    @Test
    @DisplayName("Should pass validation when valid configuration is provided")
    void validConfiguration() {
        AdminBootstrapProperties props = new AdminBootstrapProperties();
        props.setEnabled(true);
        props.setEmail("admin@flowdesk.test");
        props.setPassword("ValidSecurePass123!");
        props.setFirstName("Super");
        props.setLastName("Admin");

        assertDoesNotThrow(props::validate);
    }
}
