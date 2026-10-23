
package com.flowdesk.audit.controller;

import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.repository.UserRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private UserRepository userRepository;

    @Value("${app.security.jwt.issuer}")
    private String issuer;

    @Test
    void adminCanReadAuditEvents() throws Exception {

        UUID userId = UUID.randomUUID();

        mockCurrentUser(userId, RoleCode.ADMIN);

        String token = createToken(userId, "ADMIN");

        mockMvc.perform(
                get("/api/audit/events")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
        ).andExpect(status().isOk());
    }

    @Test
    void teamLeadCannotReadAuditEvents() throws Exception {

        UUID userId = UUID.randomUUID();

        mockCurrentUser(userId, RoleCode.TEAM_LEAD);

        String token = createToken(userId, "TEAM_LEAD");

        mockMvc.perform(
                get("/api/audit/events")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
        ).andExpect(status().isForbidden());
    }

    @Test
    void supportEngineerCannotReadAuditEvents() throws Exception {

        UUID userId = UUID.randomUUID();

        mockCurrentUser(
                userId,
                RoleCode.SUPPORT_ENGINEER
        );

        String token =
                createToken(userId, "SUPPORT_ENGINEER");

        mockMvc.perform(
                get("/api/audit/events")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
        ).andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotReadAuditEvents() throws Exception {

        UUID userId = UUID.randomUUID();

        mockCurrentUser(userId, RoleCode.EMPLOYEE);

        String token = createToken(userId, "EMPLOYEE");

        mockMvc.perform(
                get("/api/audit/events")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
        ).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestCannotReadAuditEvents()
            throws Exception {

        mockMvc.perform(
                get("/api/audit/events")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void oldAdminTokenCannotKeepRemovedAdminRole()
            throws Exception {

        UUID userId = UUID.randomUUID();

        // Database currently says EMPLOYEE.
        mockCurrentUser(userId, RoleCode.EMPLOYEE);

        // JWT still contains the old ADMIN role.
        String token = createToken(userId, "ADMIN");

        mockMvc.perform(
                get("/api/audit/events")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
        ).andExpect(status().isForbidden());
    }

    @Test
    void inactiveAdminMustBeRejected()
            throws Exception {

        UUID userId = UUID.randomUUID();

        mockCurrentUser(
                userId,
                RoleCode.ADMIN,
                UserStatus.INACTIVE
        );

        String token = createToken(userId, "ADMIN");

        mockMvc.perform(
                get("/api/audit/events")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
        ).andExpect(status().isUnauthorized());
    }

    private void mockCurrentUser(
            UUID userId,
            RoleCode roleCode
    ) {
        mockCurrentUser(
                userId,
                roleCode,
                UserStatus.ACTIVE
        );
    }

    private void mockCurrentUser(
            UUID userId,
            RoleCode roleCode,
            UserStatus status
    ) {

        User user = mock(User.class);
        Role role = mock(Role.class);

        when(user.getStatus()).thenReturn(status);

        if (status == UserStatus.ACTIVE) {
            when(user.getRoles()).thenReturn(Set.of(role));
            when(role.getCode()).thenReturn(roleCode);
        }

        when(userRepository.findWithRolesById(userId))
                .thenReturn(Optional.of(user));
    }

    private String createToken(
            UUID subject,
            String role
    ) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet
                .builder()
                .issuer(issuer)
                .subject(subject.toString())
                .issuedAt(now)
                .expiresAt(
                        now.plus(30, ChronoUnit.MINUTES)
                )
                .claim("roles", List.of(role))
                .build();

        return jwtEncoder
                .encode(
                        JwtEncoderParameters.from(claims)
                )
                .getTokenValue();
    }
}
