package com.flowdesk.user.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test verifying that direct user creation endpoints have been completely disabled
 * and accounts can only be onboarded through secure invitation flows.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserRegistrationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String VALID_REQUEST = """
            {
              "email": "new.employee@example.com",
              "password": "SecureTestPassword123",
              "firstName": "New",
              "lastName": "Employee"
            }
            """;

    @Test
    @DisplayName("Guest cannot access legacy direct creation endpoint POST /api/users")
    void guestCannotAccessDirectUserCreation() throws Exception {
        mockMvc.perform(
                post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST)
        ).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Employee cannot access legacy direct creation endpoint POST /api/users")
    void employeeCannotAccessDirectUserCreation() throws Exception {
        mockMvc.perform(
                post("/api/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST)
        ).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Administrator cannot directly create users via POST /api/users (endpoint removed -> 404)")
    void adminCannotDirectlyCreateUsersViaApiUsers() throws Exception {
        mockMvc.perform(
                post("/api/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST)
        ).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Administrator cannot directly create users via POST /api/admin/users (method not allowed -> 405)")
    void adminCannotDirectlyCreateUsersViaAdminUsers() throws Exception {
        mockMvc.perform(
                post("/api/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST)
        ).andExpect(status().isMethodNotAllowed());
    }
}
