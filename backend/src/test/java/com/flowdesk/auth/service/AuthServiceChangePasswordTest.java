package com.flowdesk.auth.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;
import com.flowdesk.auth.dto.ChangePasswordRequest;
import com.flowdesk.auth.dto.ChangePasswordResponse;
import com.flowdesk.auth.security.AuthenticatedUser;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceChangePasswordTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenService tokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    private final String currentPlainPassword = "OldPassword123!";
    private final String newPlainPassword = "NewPassword456!";
    private String encodedCurrentPassword;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        encodedCurrentPassword = passwordEncoder.encode(currentPlainPassword);

        authService = new AuthService(
                authenticationManager,
                tokenService,
                userRepository,
                passwordEncoder,
                auditService
        );
    }

    @Test
    @DisplayName("Successful password change updates hash, increments version, audits, and returns response")
    void successfulPasswordChange() {
        UUID userId = UUID.randomUUID();
        User user = new User("user@example.com", encodedCurrentPassword, "John", "Doe");
        assertEquals(1L, user.getCredentialVersion());

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        ChangePasswordRequest request = new ChangePasswordRequest(currentPlainPassword, newPlainPassword);
        ChangePasswordResponse response = authService.changePassword(userId, request);

        assertNotNull(response);
        assertEquals("Password changed successfully", response.message());

        // BCrypt encoding verified
        assertTrue(passwordEncoder.matches(newPlainPassword, user.getPasswordHash()));
        assertFalse(passwordEncoder.matches(currentPlainPassword, user.getPasswordHash()));

        // Version increment verified
        assertEquals(2L, user.getCredentialVersion());

        verify(userRepository).save(user);

        // Safe audit record verified
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).recordUserAction(
                eq(user),
                eq(AuditAction.PASSWORD_CHANGED),
                eq(AuditTargetType.USER),
                eq(user.getId()),
                eq("user@example.com"),
                metadataCaptor.capture()
        );

        Map<String, Object> metadata = metadataCaptor.getValue();
        assertEquals(2L, metadata.get("credentialVersion"));
        assertFalse(metadata.containsKey("password"));
        assertFalse(metadata.containsKey("passwordHash"));
        assertFalse(metadata.containsKey("token"));
    }

    @Test
    @DisplayName("Wrong current password is rejected with 400 Bad Request")
    void wrongCurrentPasswordRejected() {
        UUID userId = UUID.randomUUID();
        User user = new User("user@example.com", encodedCurrentPassword, "John", "Doe");

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        ChangePasswordRequest request = new ChangePasswordRequest("WrongPassword!", newPlainPassword);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.changePassword(userId, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Invalid current password", exception.getReason());

        // Password and version unchanged
        assertEquals(encodedCurrentPassword, user.getPasswordHash());
        assertEquals(1L, user.getCredentialVersion());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("Same old and new password is rejected with 400 Bad Request")
    void sameOldAndNewPasswordRejected() {
        UUID userId = UUID.randomUUID();
        User user = new User("user@example.com", encodedCurrentPassword, "John", "Doe");

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        ChangePasswordRequest request = new ChangePasswordRequest(currentPlainPassword, currentPlainPassword);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.changePassword(userId, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("New password cannot be the same as current password", exception.getReason());

        assertEquals(encodedCurrentPassword, user.getPasswordHash());
        assertEquals(1L, user.getCredentialVersion());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("Inactive account is rejected with 401 Unauthorized")
    void inactiveAccountRejected() {
        UUID userId = UUID.randomUUID();
        User user = new User("user@example.com", encodedCurrentPassword, "John", "Doe");
        user.deactivate();

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        ChangePasswordRequest request = new ChangePasswordRequest(currentPlainPassword, newPlainPassword);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.changePassword(userId, request)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Account is unavailable", exception.getReason());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("Non-existent user is rejected with 401 Unauthorized")
    void nonExistentUserRejected() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());

        ChangePasswordRequest request = new ChangePasswordRequest(currentPlainPassword, newPlainPassword);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.changePassword(userId, request)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Account is unavailable", exception.getReason());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("New password shorter than 8 characters is rejected with 400 Bad Request")
    void shortNewPasswordRejected() {
        UUID userId = UUID.randomUUID();
        User user = new User("user@example.com", encodedCurrentPassword, "John", "Doe");

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        ChangePasswordRequest request = new ChangePasswordRequest(currentPlainPassword, "short");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.changePassword(userId, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Password must be between 8 and 72 characters", exception.getReason());

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("New password longer than 72 characters is rejected with 400 Bad Request")
    void longNewPasswordRejected() {
        UUID userId = UUID.randomUUID();
        User user = new User("user@example.com", encodedCurrentPassword, "John", "Doe");

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        String tooLong = "A".repeat(73);
        ChangePasswordRequest request = new ChangePasswordRequest(currentPlainPassword, tooLong);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.changePassword(userId, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Password must be between 8 and 72 characters", exception.getReason());

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Concurrent attempts against the same user cannot silently lose version increments")
    void concurrentPasswordChangesCannotLoseVersionUpdate() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User("user@example.com", encodedCurrentPassword, "John", "Doe");

        // Simulate synchronized row lock on user
        when(userRepository.findByIdForUpdate(userId)).thenAnswer(invocation -> Optional.of(user));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // Both threads attempt to change password using the initial password
        Runnable task = () -> {
            try {
                latch.await();
                // Synchronize on user object simulating pessimistic database row locking
                synchronized (user) {
                    authService.changePassword(userId, new ChangePasswordRequest(currentPlainPassword, newPlainPassword));
                }
                successCount.incrementAndGet();
            } catch (ResponseStatusException e) {
                failCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
            }
        };

        Future<?> f1 = executor.submit(task);
        Future<?> f2 = executor.submit(task);

        latch.countDown();

        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one thread succeeds with the old password; the other fails because the password was already changed
        assertEquals(1, successCount.get(), "Exactly one change must succeed with the original password");
        assertEquals(1, failCount.get(), "The second attempt with the old password must be rejected");
        assertEquals(2L, user.getCredentialVersion(), "Credential version must be incremented exactly once");
    }
}
