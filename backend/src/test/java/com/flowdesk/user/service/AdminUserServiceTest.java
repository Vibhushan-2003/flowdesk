package com.flowdesk.user.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;
import com.flowdesk.common.dto.PageResponse;
import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.dto.AdminCreateUserRequest;
import com.flowdesk.user.dto.UpdateUserRolesRequest;
import com.flowdesk.user.dto.UpdateUserStatusRequest;
import com.flowdesk.user.dto.UserResponse;
import com.flowdesk.user.exception.DuplicateEmailException;
import com.flowdesk.user.repository.RoleRepository;
import com.flowdesk.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    private AdminUserService adminUserService;

    private Role adminRole;
    private Role employeeRole;
    private Role supportRole;

    private User actorAdmin;
    private UUID actorAdminId;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(
                userRepository,
                roleRepository,
                passwordEncoder,
                auditService
        );

        adminRole = new Role(RoleCode.ADMIN, "Platform Administrator");
        employeeRole = new Role(RoleCode.EMPLOYEE, "Standard Employee");
        supportRole = new Role(RoleCode.SUPPORT_ENGINEER, "Support Engineer");

        actorAdminId = UUID.randomUUID();
        actorAdmin = new User("admin@flowdesk.test", "hash", "Admin", "User");
        ReflectionTestUtils.setField(actorAdmin, "id", actorAdminId);
        actorAdmin.assignRole(adminRole);
    }

    @Test
    void searchUsersReturnsPaginatedUsers() {
        User user1 = new User("u1@test.com", "h1", "First", "User");
        ReflectionTestUtils.setField(user1, "id", UUID.randomUUID());
        user1.assignRole(employeeRole);

        when(userRepository.searchUsers(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(user1), PageRequest.of(0, 20), 1));

        PageResponse<UserResponse> result = adminUserService.searchUsers(
                0, 20, "first", RoleCode.EMPLOYEE, UserStatus.ACTIVE
        );

        assertNotNull(result);
        assertEquals(1, result.totalElements());
        assertEquals(1, result.content().size());
        assertEquals("u1@test.com", result.content().getFirst().email());
    }

    @Test
    void searchUsersThrowsOnInvalidPagination() {
        ResponseStatusException ex1 = assertThrows(
                ResponseStatusException.class,
                () -> adminUserService.searchUsers(-1, 20, null, null, null)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

        ResponseStatusException ex2 = assertThrows(
                ResponseStatusException.class,
                () -> adminUserService.searchUsers(0, 0, null, null, null)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
    }

    @Test
    void getUserByIdReturnsUserWhenFound() {
        UUID targetId = UUID.randomUUID();
        User target = new User("target@test.com", "h", "Target", "User");
        ReflectionTestUtils.setField(target, "id", targetId);
        target.assignRole(employeeRole);

        when(userRepository.findWithRolesById(targetId)).thenReturn(Optional.of(target));

        UserResponse response = adminUserService.getUserById(targetId);
        assertNotNull(response);
        assertEquals("target@test.com", response.email());
    }

    @Test
    void getUserByIdThrowsNotFoundWhenMissing() {
        UUID targetId = UUID.randomUUID();
        when(userRepository.findWithRolesById(targetId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> adminUserService.getUserById(targetId)
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void createUserSuccessfullyCreatesUserAndAudits() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "new.user@flowdesk.test",
                "Password123",
                "New",
                "User",
                Set.of(RoleCode.EMPLOYEE, RoleCode.SUPPORT_ENGINEER)
        );

        when(userRepository.existsByEmailIgnoreCase("new.user@flowdesk.test")).thenReturn(false);
        when(userRepository.findWithRolesById(actorAdminId)).thenReturn(Optional.of(actorAdmin));
        when(roleRepository.findByCodeIn(request.roles())).thenReturn(List.of(employeeRole, supportRole));
        when(passwordEncoder.encode("Password123")).thenReturn("encodedPassword");

        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
            return u;
        });

        UserResponse response = adminUserService.createUser(request, actorAdminId);

        assertNotNull(response);
        assertEquals("new.user@flowdesk.test", response.email());
        assertEquals(2, response.roles().size());
        assertTrue(response.roles().contains(RoleCode.EMPLOYEE));
        assertTrue(response.roles().contains(RoleCode.SUPPORT_ENGINEER));

        verify(auditService).recordUserAction(
                eq(actorAdmin),
                eq(AuditAction.USER_CREATED),
                eq(AuditTargetType.USER),
                any(UUID.class),
                eq("new.user@flowdesk.test"),
                any()
        );
    }

    @Test
    void createUserThrowsOnDuplicateEmail() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "duplicate@test.com",
                "Password123",
                "Dup",
                "User",
                Set.of(RoleCode.EMPLOYEE)
        );

        when(userRepository.existsByEmailIgnoreCase("duplicate@test.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> adminUserService.createUser(request, actorAdminId));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateUserRolesSuccessfullyUpdatesAndAudits() {
        UUID targetId = UUID.randomUUID();
        User target = new User("target@test.com", "h", "Target", "User");
        ReflectionTestUtils.setField(target, "id", targetId);
        target.assignRole(employeeRole);

        when(userRepository.findWithRolesById(targetId)).thenReturn(Optional.of(target));
        when(userRepository.findWithRolesById(actorAdminId)).thenReturn(Optional.of(actorAdmin));
        when(roleRepository.findByCodeIn(Set.of(RoleCode.SUPPORT_ENGINEER))).thenReturn(List.of(supportRole));
        when(userRepository.saveAndFlush(target)).thenReturn(target);

        UpdateUserRolesRequest request = new UpdateUserRolesRequest(Set.of(RoleCode.SUPPORT_ENGINEER));
        UserResponse response = adminUserService.updateUserRoles(targetId, request, actorAdminId);

        assertNotNull(response);
        assertEquals(Set.of(RoleCode.SUPPORT_ENGINEER), response.roles());

        verify(auditService).recordUserAction(
                eq(actorAdmin),
                eq(AuditAction.USER_ROLES_UPDATED),
                eq(AuditTargetType.USER),
                eq(targetId),
                eq("target@test.com"),
                any()
        );
    }

    @Test
    void updateUserRolesPreventsSelfDemotion() {
        when(userRepository.findWithRolesById(actorAdminId)).thenReturn(Optional.of(actorAdmin));

        UpdateUserRolesRequest request = new UpdateUserRolesRequest(Set.of(RoleCode.EMPLOYEE));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> adminUserService.updateUserRoles(actorAdminId, request, actorAdminId)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("cannot remove their own ADMIN role"));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateUserRolesPreventsRemovingLastActiveAdmin() {
        UUID targetAdminId = UUID.randomUUID();
        User targetAdmin = new User("other.admin@flowdesk.test", "h", "Other", "Admin");
        ReflectionTestUtils.setField(targetAdmin, "id", targetAdminId);
        targetAdmin.assignRole(adminRole);

        when(userRepository.findWithRolesById(targetAdminId)).thenReturn(Optional.of(targetAdmin));
        when(userRepository.findWithRolesById(actorAdminId)).thenReturn(Optional.of(actorAdmin));
        when(userRepository.countByRolesCodeAndStatus(RoleCode.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        UpdateUserRolesRequest request = new UpdateUserRolesRequest(Set.of(RoleCode.EMPLOYEE));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> adminUserService.updateUserRoles(targetAdminId, request, actorAdminId)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("last active administrator"));
        verify(roleRepository).findByCodeForUpdate(RoleCode.ADMIN);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateUserRolesAllowsDemotingAdminWhenMultipleActiveAdminsExist() {
        UUID targetAdminId = UUID.randomUUID();
        User targetAdmin = new User("other.admin@flowdesk.test", "h", "Other", "Admin");
        ReflectionTestUtils.setField(targetAdmin, "id", targetAdminId);
        targetAdmin.assignRole(adminRole);

        when(userRepository.findWithRolesById(targetAdminId)).thenReturn(Optional.of(targetAdmin));
        when(userRepository.findWithRolesById(actorAdminId)).thenReturn(Optional.of(actorAdmin));
        when(roleRepository.findByCodeForUpdate(RoleCode.ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.countByRolesCodeAndStatus(RoleCode.ADMIN, UserStatus.ACTIVE)).thenReturn(2L);
        when(roleRepository.findByCodeIn(Set.of(RoleCode.EMPLOYEE))).thenReturn(List.of(employeeRole));
        when(userRepository.saveAndFlush(targetAdmin)).thenReturn(targetAdmin);

        UpdateUserRolesRequest request = new UpdateUserRolesRequest(Set.of(RoleCode.EMPLOYEE));
        UserResponse response = adminUserService.updateUserRoles(targetAdminId, request, actorAdminId);

        assertNotNull(response);
        assertEquals(Set.of(RoleCode.EMPLOYEE), response.roles());
        verify(userRepository).saveAndFlush(targetAdmin);
    }

    @Test
    void updateUserStatusDeactivatesUserAndAudits() {
        UUID targetId = UUID.randomUUID();
        User target = new User("emp@test.com", "h", "Emp", "User");
        ReflectionTestUtils.setField(target, "id", targetId);
        target.assignRole(employeeRole);

        when(userRepository.findWithRolesById(targetId)).thenReturn(Optional.of(target));
        when(userRepository.findWithRolesById(actorAdminId)).thenReturn(Optional.of(actorAdmin));
        when(userRepository.saveAndFlush(target)).thenReturn(target);

        UpdateUserStatusRequest request = new UpdateUserStatusRequest(UserStatus.INACTIVE);
        UserResponse response = adminUserService.updateUserStatus(targetId, request, actorAdminId);

        assertNotNull(response);
        assertEquals(UserStatus.INACTIVE, response.status());

        verify(auditService).recordUserAction(
                eq(actorAdmin),
                eq(AuditAction.USER_DEACTIVATED),
                eq(AuditTargetType.USER),
                eq(targetId),
                eq("emp@test.com"),
                any()
        );
    }

    @Test
    void updateUserStatusPreventsSelfDeactivation() {
        when(userRepository.findWithRolesById(actorAdminId)).thenReturn(Optional.of(actorAdmin));

        UpdateUserStatusRequest request = new UpdateUserStatusRequest(UserStatus.INACTIVE);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> adminUserService.updateUserStatus(actorAdminId, request, actorAdminId)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("cannot deactivate their own account"));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateUserStatusPreventsDeactivatingLastActiveAdmin() {
        UUID targetAdminId = UUID.randomUUID();
        User targetAdmin = new User("target.admin@test.com", "h", "T", "A");
        ReflectionTestUtils.setField(targetAdmin, "id", targetAdminId);
        targetAdmin.assignRole(adminRole);

        when(userRepository.findWithRolesById(targetAdminId)).thenReturn(Optional.of(targetAdmin));
        when(userRepository.findWithRolesById(actorAdminId)).thenReturn(Optional.of(actorAdmin));
        when(userRepository.countByRolesCodeAndStatus(RoleCode.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        UpdateUserStatusRequest request = new UpdateUserStatusRequest(UserStatus.INACTIVE);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> adminUserService.updateUserStatus(targetAdminId, request, actorAdminId)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("last active administrator"));
        verify(roleRepository).findByCodeForUpdate(RoleCode.ADMIN);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateUserStatusActivatesUserAndAudits() {
        UUID targetId = UUID.randomUUID();
        User target = new User("inactive@test.com", "h", "I", "U");
        ReflectionTestUtils.setField(target, "id", targetId);
        target.assignRole(employeeRole);
        target.deactivate();

        when(userRepository.findWithRolesById(targetId)).thenReturn(Optional.of(target));
        when(userRepository.findWithRolesById(actorAdminId)).thenReturn(Optional.of(actorAdmin));
        when(userRepository.saveAndFlush(target)).thenReturn(target);

        UpdateUserStatusRequest request = new UpdateUserStatusRequest(UserStatus.ACTIVE);
        UserResponse response = adminUserService.updateUserStatus(targetId, request, actorAdminId);

        assertNotNull(response);
        assertEquals(UserStatus.ACTIVE, response.status());

        verify(auditService).recordUserAction(
                eq(actorAdmin),
                eq(AuditAction.USER_ACTIVATED),
                eq(AuditTargetType.USER),
                eq(targetId),
                eq("inactive@test.com"),
                any()
        );
    }
}
