package com.flowdesk.user.bootstrap;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;
import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.dto.UserResponse;
import com.flowdesk.user.repository.RoleRepository;
import com.flowdesk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    private AdminBootstrapService bootstrapService;

    private AdminBootstrapProperties validProps;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        bootstrapService = new AdminBootstrapService(
                userRepository,
                roleRepository,
                passwordEncoder,
                auditService
        );

        validProps = new AdminBootstrapProperties();
        validProps.setEnabled(true);
        validProps.setEmail("admin@flowdesk.test");
        validProps.setPassword("StrongAdminPassword123!");
        validProps.setFirstName("First");
        validProps.setLastName("Admin");

        adminRole = new Role(RoleCode.ADMIN, "Platform administrator");
    }

    @Test
    @DisplayName("Should throw exception when ADMIN role is missing from database")
    void missingAdminRole() {
        when(roleRepository.findByCodeForUpdate(RoleCode.ADMIN)).thenReturn(Optional.empty());

        AdminBootstrapException ex = assertThrows(
                AdminBootstrapException.class,
                () -> bootstrapService.provisionFirstAdmin(validProps)
        );

        assertTrue(ex.getMessage().contains("ADMIN role is not seeded"));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should refuse bootstrap when any user already exists in database")
    void refuseWhenUsersExist() {
        when(roleRepository.findByCodeForUpdate(RoleCode.ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.count()).thenReturn(1L);

        AdminBootstrapException ex = assertThrows(
                AdminBootstrapException.class,
                () -> bootstrapService.provisionFirstAdmin(validProps)
        );

        assertTrue(ex.getMessage().contains("Database is not in initial state"));
        assertTrue(ex.getMessage().contains("1 user(s) already exist"));
        verify(userRepository, never()).saveAndFlush(any());
        verify(auditService, never()).recordSystemAction(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should successfully create ACTIVE admin and record audit when 0 users exist")
    void successfulBootstrap() {
        when(roleRepository.findByCodeForUpdate(RoleCode.ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("StrongAdminPassword123!")).thenReturn("bcrypt_hashed_pass");

        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = bootstrapService.provisionFirstAdmin(validProps);

        assertNotNull(response);
        assertEquals("admin@flowdesk.test", response.email());
        assertEquals("First", response.firstName());
        assertEquals("Admin", response.lastName());
        assertEquals(UserStatus.ACTIVE, response.status());
        assertEquals(Set.of(RoleCode.ADMIN), response.roles());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals("admin@flowdesk.test", savedUser.getEmail());
        assertEquals("bcrypt_hashed_pass", savedUser.getPasswordHash());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());
        assertTrue(savedUser.getRoles().contains(adminRole));

        verify(auditService).recordSystemAction(
                eq(AuditAction.USER_CREATED),
                eq(AuditTargetType.USER),
                any(),
                eq("admin@flowdesk.test"),
                any()
        );
    }

    @Test
    @DisplayName("Should rollback and bubble exception if audit service throws")
    void auditFailureRollback() {
        when(roleRepository.findByCodeForUpdate(RoleCode.ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(auditService.recordSystemAction(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Audit database write failed"));

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> bootstrapService.provisionFirstAdmin(validProps)
        );

        assertEquals("Audit database write failed", ex.getMessage());
    }
}
