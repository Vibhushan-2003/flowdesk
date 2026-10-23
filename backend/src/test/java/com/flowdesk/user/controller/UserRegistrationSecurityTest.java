
package com.flowdesk.user.controller;

import com.flowdesk.user.service.UserService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserRegistrationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static final String VALID_REQUEST = """
            {
              "email": "new.employee@example.com",
              "password": "SecureTestPassword123",
              "firstName": "New",
              "lastName": "Employee"
            }
            """;

    @Test
    void guestCannotCreateUsers() throws Exception {

        mockMvc.perform(
                post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST)
        ).andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void employeeCannotCreateUsers() throws Exception {

        mockMvc.perform(
                post("/api/users")
                        .with(
                                jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_EMPLOYEE"
                                        )
                                )
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST)
        ).andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    void supportEngineerCannotCreateUsers() throws Exception {

        mockMvc.perform(
                post("/api/users")
                        .with(
                                jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_SUPPORT_ENGINEER"
                                        )
                                )
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST)
        ).andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    void teamLeadCannotCreateUsers() throws Exception {

        mockMvc.perform(
                post("/api/users")
                        .with(
                                jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_TEAM_LEAD"
                                        )
                                )
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST)
        ).andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    void adminCanCreateUsers() throws Exception {

        mockMvc.perform(
                post("/api/users")
                        .with(
                                jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                )
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST)
        ).andExpect(status().isCreated());

        verify(userService, times(1))
                .createUser(any());
    }
}
