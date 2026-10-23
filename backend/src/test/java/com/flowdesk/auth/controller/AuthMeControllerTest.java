
package com.flowdesk.auth.controller;

import com.flowdesk.auth.service.AuthService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthMeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void meReturnsCurrentAuthoritiesInsteadOfOldJwtRoles()
            throws Exception {

        UUID userId = UUID.randomUUID();

        mockMvc.perform(
                get("/api/auth/me")
                        .with(
                                jwt()
                                        .jwt(Jwt.withTokenValue("test-token")
                                                .header("alg", "none")
                                                .subject(userId.toString())
                                                .claim(
                                                        "email",
                                                        "employee@example.com"
                                                )
                                                .claim(
                                                        "roles",
                                                        java.util.List.of("ADMIN")
                                                )
                                                .build()
                                        )
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_EMPLOYEE"
                                                )
                                        )
                        )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId")
                        .value(userId.toString()))
                .andExpect(jsonPath("$.email")
                        .value("employee@example.com"))
                .andExpect(jsonPath("$.roles[0]")
                        .value("EMPLOYEE"))
                .andExpect(jsonPath("$.roles.length()")
                        .value(1));
    }
}
