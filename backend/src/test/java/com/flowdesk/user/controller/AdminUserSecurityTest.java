package com.flowdesk.user.controller;

import com.flowdesk.common.dto.PageResponse;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.dto.UserResponse;
import com.flowdesk.user.service.AdminUserService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUserSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private com.flowdesk.user.service.UserInvitationService userInvitationService;

    private static final UUID TARGET_USER_ID = UUID.randomUUID();
    private static final UUID ACTOR_ADMIN_ID = UUID.randomUUID();

    private static final String CREATE_USER_BODY = """
            {
              "email": "jane.doe@flowdesk.test",
              "password": "Password123!",
              "firstName": "Jane",
              "lastName": "Doe",
              "roles": ["EMPLOYEE", "SUPPORT_ENGINEER"]
            }
            """;

    private static final String UPDATE_ROLES_BODY = """
            {
              "roles": ["SUPPORT_ENGINEER"]
            }
            """;

    private static final String UPDATE_STATUS_BODY = """
            {
              "status": "INACTIVE"
            }
            """;

    @Test
    void guestCannotAccessAnyAdminUserEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/users/" + TARGET_USER_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_USER_BODY))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/admin/users/" + TARGET_USER_ID + "/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_ROLES_BODY))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/admin/users/" + TARGET_USER_ID + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_STATUS_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminUserService);
    }

    @Test
    void employeeForbiddenFromAdminUserEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_USER_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminUserService);
    }

    @Test
    void supportEngineerForbiddenFromAdminUserEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/users/" + TARGET_USER_ID + "/roles")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_ROLES_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminUserService);
    }

    @Test
    void teamLeadForbiddenFromAdminUserEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEAM_LEAD"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/users/" + TARGET_USER_ID + "/status")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TEAM_LEAD")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_STATUS_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminUserService);
    }

    @Test
    void adminCanAccessAllAdminUserEndpoints() throws Exception {
        UserResponse sampleResponse = new UserResponse(
                TARGET_USER_ID,
                "jane.doe@flowdesk.test",
                "Jane",
                "Doe",
                UserStatus.ACTIVE,
                Set.of(RoleCode.EMPLOYEE),
                Instant.now()
        );

        when(adminUserService.searchUsers(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(sampleResponse), 0, 20, 1, 1, true, true));
        when(adminUserService.getUserById(TARGET_USER_ID)).thenReturn(sampleResponse);
        when(adminUserService.createUser(any(), any())).thenReturn(sampleResponse);
        when(adminUserService.updateUserRoles(any(), any(), any())).thenReturn(sampleResponse);
        when(adminUserService.updateUserStatus(any(), any(), any())).thenReturn(sampleResponse);

        // GET directory
        mockMvc.perform(get("/api/admin/users")
                        .with(jwt().jwt(builder -> builder.subject(ACTOR_ADMIN_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());

        // GET single user
        mockMvc.perform(get("/api/admin/users/" + TARGET_USER_ID)
                        .with(jwt().jwt(builder -> builder.subject(ACTOR_ADMIN_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());

        // POST create user
        mockMvc.perform(post("/api/admin/users")
                        .with(jwt().jwt(builder -> builder.subject(ACTOR_ADMIN_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_USER_BODY))
                .andExpect(status().isCreated());

        // PUT update roles
        mockMvc.perform(put("/api/admin/users/" + TARGET_USER_ID + "/roles")
                        .with(jwt().jwt(builder -> builder.subject(ACTOR_ADMIN_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_ROLES_BODY))
                .andExpect(status().isOk());

        // PATCH update status
        mockMvc.perform(patch("/api/admin/users/" + TARGET_USER_ID + "/status")
                        .with(jwt().jwt(builder -> builder.subject(ACTOR_ADMIN_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_STATUS_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedUserCannotAccessInvitationEndpoints() throws Exception {
        String body = """
                {
                  "email": "invitee@flowdesk.test",
                  "firstName": "Invitee",
                  "lastName": "User",
                  "roles": ["EMPLOYEE"]
                }
                """;

        mockMvc.perform(post("/api/admin/users/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/users/" + TARGET_USER_ID + "/invitations/resend"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userInvitationService);
    }

    @Test
    void nonAdminUserCannotAccessInvitationEndpoints() throws Exception {
        String body = """
                {
                  "email": "invitee@flowdesk.test",
                  "firstName": "Invitee",
                  "lastName": "User",
                  "roles": ["EMPLOYEE"]
                }
                """;

        mockMvc.perform(post("/api/admin/users/invitations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/users/" + TARGET_USER_ID + "/invitations/resend")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userInvitationService);
    }

    @Test
    void adminCanAccessInvitationEndpoints() throws Exception {
        String body = """
                {
                  "email": "invitee@flowdesk.test",
                  "firstName": "Invitee",
                  "lastName": "User",
                  "roles": ["EMPLOYEE"]
                }
                """;

        when(userInvitationService.createInvitation(any(), any()))
                .thenReturn(new UserResponse(
                        UUID.randomUUID(),
                        "invitee@flowdesk.test",
                        "Invitee",
                        "User",
                        UserStatus.INVITED,
                        Set.of(RoleCode.EMPLOYEE),
                        Instant.now()
                ));

        when(userInvitationService.resendInvitation(any(), any()))
                .thenReturn(new com.flowdesk.user.dto.ResendInvitationResponse("Invitation resent successfully"));

        mockMvc.perform(post("/api/admin/users/invitations")
                        .with(jwt().jwt(builder -> builder.subject(ACTOR_ADMIN_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/users/" + TARGET_USER_ID + "/invitations/resend")
                        .with(jwt().jwt(builder -> builder.subject(ACTOR_ADMIN_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
