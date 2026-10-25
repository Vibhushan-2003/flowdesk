package com.flowdesk.auth.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;
import com.flowdesk.auth.config.PasswordResetProperties;
import com.flowdesk.auth.domain.PasswordResetToken;
import com.flowdesk.auth.dto.ForgotPasswordRequest;
import com.flowdesk.auth.dto.ForgotPasswordResponse;
import com.flowdesk.auth.dto.ResetPasswordRequest;
import com.flowdesk.auth.dto.ResetPasswordResponse;
import com.flowdesk.auth.repository.PasswordResetTokenRepository;
import com.flowdesk.email.exception.EmailDeliveryException;
import com.flowdesk.email.model.EmailDeliveryResult;
import com.flowdesk.email.model.EmailMessage;
import com.flowdesk.email.service.EmailSender;
import com.flowdesk.email.template.EmailTemplateService;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private EmailSender emailSender;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private AuditService auditService;

    private PasswordEncoder passwordEncoder;
    private PasswordResetProperties properties;
    private PasswordResetService resetService;

    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        properties = new PasswordResetProperties();
        properties.setFrontendOrigin("http://localhost:5173");
        properties.setTokenValidityMinutes(15);
        properties.setTokenEntropyBytes(32);

        resetService = new PasswordResetService(
                userRepository,
                tokenRepository,
                passwordEncoder,
                emailSender,
                emailTemplateService,
                properties,
                auditService
        );
    }

    private User createTestUser(String email, String rawPassword, UserStatus status) {
        User user = new User(email, passwordEncoder.encode(rawPassword), "Test", "Employee");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        if (status == UserStatus.INACTIVE) {
            user.deactivate();
        }
        return user;
    }

    @Test
    @DisplayName("Request password reset for existing active user creates token, locks user row, sends email, and logs audit")
    void requestPasswordResetExistingActiveUser() {
        User user = createTestUser("employee@flowdesk.test", "CurrentPass123!", UserStatus.ACTIVE);
        when(userRepository.findByEmailIgnoreCase("employee@flowdesk.test")).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        EmailMessage emailMsg = EmailMessage.of(
                "employee@flowdesk.test",
                "FlowDesk - Password Reset Request",
                "Reset",
                "<html>Reset</html>"
        );

        when(emailTemplateService.buildPasswordResetEmail(eq("employee@flowdesk.test"), any(), any()))
                .thenReturn(emailMsg);
        when(emailSender.send(any(EmailMessage.class)))
                .thenReturn(EmailDeliveryResult.accepted("msg-101", "employee@flowdesk.test"));

        ForgotPasswordResponse response = resetService.requestPasswordReset(
                new ForgotPasswordRequest("  Employee@FlowDesk.TEST  ")
        );

        assertNotNull(response);
        assertEquals(ForgotPasswordResponse.genericSuccess().message(), response.message());

        // Pessimistic user row lock was acquired to serialize concurrent requests
        verify(userRepository).findByIdForUpdate(user.getId());

        // Invalidate active tokens for this user first
        verify(tokenRepository).invalidateActiveTokensForUser(eq(user.getId()), any(Instant.class));

        // Save new token
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertEquals(user, savedToken.getUser());
        assertNotNull(savedToken.getTokenHash());
        assertEquals(64, savedToken.getTokenHash().length()); // SHA-256 hex length
        assertNotNull(savedToken.getExpiresAt());
        assertNull(savedToken.getConsumedAt()); // Active, not invalidated

        // Verify email sent
        verify(emailSender).send(any(EmailMessage.class));

        // Verify audit event
        verify(auditService).recordSystemAction(
                eq(AuditAction.PASSWORD_RESET_REQUESTED),
                eq(AuditTargetType.USER),
                eq(user.getId()),
                eq(user.getEmail()),
                any()
        );
    }

    @Test
    @DisplayName("Request password reset with disabled email delivery invalidates token and does not audit dispatch")
    void requestPasswordResetDeliveryDisabledInvalidatesToken() {
        User user = createTestUser("employee@flowdesk.test", "CurrentPass123!", UserStatus.ACTIVE);
        when(userRepository.findByEmailIgnoreCase("employee@flowdesk.test")).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        EmailMessage emailMsg = EmailMessage.of("employee@flowdesk.test", "Subject", "Text", "Html");
        when(emailTemplateService.buildPasswordResetEmail(any(), any(), any())).thenReturn(emailMsg);
        when(emailSender.send(any())).thenReturn(EmailDeliveryResult.disabled("employee@flowdesk.test"));

        ForgotPasswordResponse response = resetService.requestPasswordReset(
                new ForgotPasswordRequest("employee@flowdesk.test")
        );

        // Generic response preserved
        assertEquals(ForgotPasswordResponse.genericSuccess().message(), response.message());

        // Token was initially saved and then invalidated
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository, atLeast(2)).save(tokenCaptor.capture());
        List<PasswordResetToken> savedTokens = tokenCaptor.getAllValues();
        PasswordResetToken finalTokenState = savedTokens.get(savedTokens.size() - 1);
        assertTrue(finalTokenState.isConsumed(), "Token must be invalidated when delivery is disabled");

        // Audit dispatch must NOT be recorded if email was suppressed
        verify(auditService, never()).recordSystemAction(
                eq(AuditAction.PASSWORD_RESET_REQUESTED), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("Request password reset with failed email delivery invalidates token and does not audit dispatch")
    void requestPasswordResetDeliveryFailedInvalidatesToken() {
        User user = createTestUser("employee@flowdesk.test", "CurrentPass123!", UserStatus.ACTIVE);
        when(userRepository.findByEmailIgnoreCase("employee@flowdesk.test")).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        EmailMessage emailMsg = EmailMessage.of("employee@flowdesk.test", "Subject", "Text", "Html");
        when(emailTemplateService.buildPasswordResetEmail(any(), any(), any())).thenReturn(emailMsg);
        when(emailSender.send(any())).thenReturn(EmailDeliveryResult.failed("msg-99", "employee@flowdesk.test", "Connection rejected"));

        ForgotPasswordResponse response = resetService.requestPasswordReset(
                new ForgotPasswordRequest("employee@flowdesk.test")
        );

        // Generic response preserved
        assertEquals(ForgotPasswordResponse.genericSuccess().message(), response.message());

        // Token must be invalidated
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository, atLeast(2)).save(tokenCaptor.capture());
        PasswordResetToken finalTokenState = tokenCaptor.getAllValues().get(tokenCaptor.getAllValues().size() - 1);
        assertTrue(finalTokenState.isConsumed(), "Token must be invalidated when delivery fails");

        // No false audit dispatch
        verify(auditService, never()).recordSystemAction(
                eq(AuditAction.PASSWORD_RESET_REQUESTED), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("Request password reset handles EmailDeliveryException gracefully by invalidating token and preserving generic response")
    void requestPasswordResetEmailFailureHandledGracefully() {
        User user = createTestUser("employee@flowdesk.test", "CurrentPass123!", UserStatus.ACTIVE);
        when(userRepository.findByEmailIgnoreCase("employee@flowdesk.test")).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        EmailMessage emailMsg = EmailMessage.of(
                "employee@flowdesk.test",
                "FlowDesk - Password Reset Request",
                "Reset",
                "<html>Reset</html>"
        );

        when(emailTemplateService.buildPasswordResetEmail(eq("employee@flowdesk.test"), any(), any()))
                .thenReturn(emailMsg);
        doThrow(new EmailDeliveryException("SMTP connection timed out"))
                .when(emailSender).send(any());

        ForgotPasswordResponse response = resetService.requestPasswordReset(
                new ForgotPasswordRequest("employee@flowdesk.test")
        );

        // Still returns generic success to prevent side-channel enumeration
        assertEquals(ForgotPasswordResponse.genericSuccess().message(), response.message());

        // Token was invalidated so undelivered token cannot remain active
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository, atLeast(2)).save(tokenCaptor.capture());
        PasswordResetToken finalTokenState = tokenCaptor.getAllValues().get(tokenCaptor.getAllValues().size() - 1);
        assertTrue(finalTokenState.isConsumed(), "Token must be invalidated on exception");

        // No false audit dispatch
        verify(auditService, never()).recordSystemAction(
                eq(AuditAction.PASSWORD_RESET_REQUESTED), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("Request password reset for non-existent user returns generic message and sends no email")
    void requestPasswordResetNonExistentUserDefendsEnumeration() {
        when(userRepository.findByEmailIgnoreCase("unknown@flowdesk.test")).thenReturn(Optional.empty());

        ForgotPasswordResponse response = resetService.requestPasswordReset(
                new ForgotPasswordRequest("unknown@flowdesk.test")
        );

        assertEquals(ForgotPasswordResponse.genericSuccess().message(), response.message());
        verify(tokenRepository, never()).save(any());
        verify(emailSender, never()).send(any());
        verify(auditService, never()).recordSystemAction(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Request password reset for inactive user returns generic message and sends no email")
    void requestPasswordResetInactiveUserDefendsEnumeration() {
        User user = createTestUser("inactive@flowdesk.test", "CurrentPass123!", UserStatus.INACTIVE);
        when(userRepository.findByEmailIgnoreCase("inactive@flowdesk.test")).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        ForgotPasswordResponse response = resetService.requestPasswordReset(
                new ForgotPasswordRequest("inactive@flowdesk.test")
        );

        assertEquals(ForgotPasswordResponse.genericSuccess().message(), response.message());
        verify(tokenRepository, never()).save(any());
        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("Reset password acquires locks in strict order (User row FIRST, token row SECOND)")
    void resetPasswordConsistentLockOrder() {
        User user = createTestUser("employee@flowdesk.test", "OldPass123!", UserStatus.ACTIVE);

        String rawToken = "test_raw_token_value_with_entropy_12345678";
        String tokenHash = PasswordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, now.plus(Duration.ofMinutes(10)));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(token));

        EmailMessage securityNotice = EmailMessage.of(
                "employee@flowdesk.test",
                "FlowDesk Security Alert - Password Changed",
                "Notice",
                "<html>Notice</html>"
        );
        when(emailTemplateService.buildSecurityNoticeEmail(any(), any(), any())).thenReturn(securityNotice);

        ResetPasswordResponse response = resetService.resetPassword(
                new ResetPasswordRequest(rawToken, "NewSecurePass456!", "NewSecurePass456!")
        );

        assertNotNull(response);

        // Strict lock order verification: users row locked BEFORE token row locked
        InOrder inOrder = inOrder(userRepository, tokenRepository);
        inOrder.verify(userRepository).findByIdForUpdate(user.getId());
        inOrder.verify(tokenRepository).findByTokenHashForUpdate(tokenHash);
    }

    @Test
    @DisplayName("Reset password with valid token updates password, bumps credential version, and marks consumed")
    void resetPasswordSuccess() {
        User user = createTestUser("employee@flowdesk.test", "OldPass123!", UserStatus.ACTIVE);
        long initialCredentialVersion = user.getCredentialVersion();

        String rawToken = "test_raw_token_value_with_entropy_12345678";
        String tokenHash = PasswordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, now.plus(Duration.ofMinutes(10)));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(token));

        EmailMessage securityNotice = EmailMessage.of(
                "employee@flowdesk.test",
                "FlowDesk Security Alert - Password Changed",
                "Notice",
                "<html>Notice</html>"
        );
        when(emailTemplateService.buildSecurityNoticeEmail(any(), any(), any())).thenReturn(securityNotice);

        ResetPasswordResponse response = resetService.resetPassword(
                new ResetPasswordRequest(rawToken, "NewSecurePass456!", "NewSecurePass456!")
        );

        assertNotNull(response);
        assertEquals(ResetPasswordResponse.defaultSuccess().message(), response.message());

        // Password hash updated
        assertTrue(passwordEncoder.matches("NewSecurePass456!", user.getPasswordHash()));

        // Credential version bumped to invalidate existing JWTs
        assertEquals(initialCredentialVersion + 1, user.getCredentialVersion());

        // Token marked consumed
        assertTrue(token.isConsumed());

        // Saved changes
        verify(userRepository).save(user);
        verify(tokenRepository).save(token);

        // Security notification email sent
        verify(emailSender).send(any(EmailMessage.class));

        // Audit event recorded
        verify(auditService).recordSystemAction(
                eq(AuditAction.PASSWORD_RESET_COMPLETED),
                eq(AuditTargetType.USER),
                eq(user.getId()),
                eq(user.getEmail()),
                any()
        );
    }

    @Test
    @DisplayName("Reset password does not fail if security notification email fails")
    void resetPasswordSecurityNotificationFailureDoesNotThrow() {
        User user = createTestUser("employee@flowdesk.test", "OldPass123!", UserStatus.ACTIVE);
        String rawToken = "valid_token_value_1234567890123456";
        String tokenHash = PasswordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, now.plus(Duration.ofMinutes(10)));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(token));

        EmailMessage securityNotice = EmailMessage.of("employee@flowdesk.test", "Subject", "Body", "Html");
        when(emailTemplateService.buildSecurityNoticeEmail(any(), any(), any())).thenReturn(securityNotice);
        doThrow(new RuntimeException("SMTP transport failure")).when(emailSender).send(any());

        // Even though email throws, resetPassword must NOT fail or throw exception
        ResetPasswordResponse response = resetService.resetPassword(
                new ResetPasswordRequest(rawToken, "NewSecurePass456!", "NewSecurePass456!")
        );

        assertNotNull(response);
        assertEquals(ResetPasswordResponse.defaultSuccess().message(), response.message());
        assertTrue(passwordEncoder.matches("NewSecurePass456!", user.getPasswordHash()));
        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
    }

    @Test
    @DisplayName("Reset password with invalid token throws 400 Bad Request")
    void resetPasswordInvalidTokenThrows() {
        String rawToken = "non_existent_token";
        String tokenHash = PasswordResetService.hashToken(rawToken);

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                resetService.resetPassword(
                        new ResetPasswordRequest(rawToken, "NewSecurePass456!", "NewSecurePass456!")
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Invalid or expired password reset token", ex.getReason());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reset password with expired token throws 400 Bad Request")
    void resetPasswordExpiredTokenThrows() {
        User user = createTestUser("employee@flowdesk.test", "OldPass123!", UserStatus.ACTIVE);
        String rawToken = "expired_token";
        String tokenHash = PasswordResetService.hashToken(rawToken);

        // Token expired in the past
        PasswordResetToken token = new PasswordResetToken(user, tokenHash, now.minus(Duration.ofMinutes(5)));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(token));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                resetService.resetPassword(
                        new ResetPasswordRequest(rawToken, "NewSecurePass456!", "NewSecurePass456!")
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Invalid or expired password reset token", ex.getReason());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reset password with already consumed token throws 400 Bad Request")
    void resetPasswordAlreadyConsumedTokenThrows() {
        User user = createTestUser("employee@flowdesk.test", "OldPass123!", UserStatus.ACTIVE);
        String rawToken = "already_used_token";
        String tokenHash = PasswordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, now.plus(Duration.ofMinutes(10)));
        token.consume(now.minus(Duration.ofMinutes(2))); // already consumed

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(token));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                resetService.resetPassword(
                        new ResetPasswordRequest(rawToken, "NewSecurePass456!", "NewSecurePass456!")
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Invalid or expired password reset token", ex.getReason());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reset password with password mismatch throws 400 Bad Request")
    void resetPasswordMismatchThrows() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                resetService.resetPassword(
                        new ResetPasswordRequest("token", "NewSecurePass456!", "DifferentPass456!")
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Passwords do not match", ex.getReason());
        verifyNoInteractions(tokenRepository);
    }

    @Test
    @DisplayName("Reset password reusing current password throws 400 Bad Request")
    void resetPasswordReusingCurrentPasswordThrows() {
        User user = createTestUser("employee@flowdesk.test", "CurrentPass123!", UserStatus.ACTIVE);
        String rawToken = "valid_token";
        String tokenHash = PasswordResetService.hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, now.plus(Duration.ofMinutes(10)));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(token));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                resetService.resetPassword(
                        new ResetPasswordRequest(rawToken, "CurrentPass123!", "CurrentPass123!")
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("New password cannot be the same as current password", ex.getReason());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cleanup old tokens passes cutoff and current timestamp to ensure unexpired tokens cannot be deleted")
    void cleanupOldTokensPassesSafeCutoffAndNow() {
        resetService.cleanupOldTokens(Duration.ofDays(14));

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(tokenRepository).deleteTokensOlderThan(cutoffCaptor.capture(), nowCaptor.capture());

        Instant cutoff = cutoffCaptor.getValue();
        Instant current = nowCaptor.getValue();

        assertTrue(cutoff.isBefore(current), "Cutoff must be in the past relative to now");
        long daysDiff = Duration.between(cutoff, current).toDays();
        assertEquals(14, daysDiff);
    }

    @Test
    @DisplayName("Cleanup old tokens with negative or null retention falls back safely to default 7 days")
    void cleanupOldTokensSafelyDefaultsRetention() {
        resetService.cleanupOldTokens(Duration.ofDays(-5));

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(tokenRepository).deleteTokensOlderThan(cutoffCaptor.capture(), nowCaptor.capture());

        Instant cutoff = cutoffCaptor.getValue();
        Instant current = nowCaptor.getValue();

        assertTrue(cutoff.isBefore(current));
        long daysDiff = Duration.between(cutoff, current).toDays();
        assertEquals(7, daysDiff, "Should fall back to safe 7-day retention");
    }

    @Test
    @DisplayName("Concurrent reset requests for same user serialize without errors")
    void concurrentResetRequestsForSameUserSerialize() throws Exception {
        User user = createTestUser("employee@flowdesk.test", "CurrentPass123!", UserStatus.ACTIVE);
        when(userRepository.findByEmailIgnoreCase("employee@flowdesk.test")).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        EmailMessage emailMsg = EmailMessage.of("employee@flowdesk.test", "Subject", "Body", "Html");
        when(emailTemplateService.buildPasswordResetEmail(any(), any(), any())).thenReturn(emailMsg);
        when(emailSender.send(any())).thenReturn(EmailDeliveryResult.accepted("msg-1", "employee@flowdesk.test"));

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ForgotPasswordResponse resp = resetService.requestPasswordReset(
                            new ForgotPasswordRequest("employee@flowdesk.test")
                    );
                    if (ForgotPasswordResponse.genericSuccess().message().equals(resp.message())) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed);
        assertEquals(threadCount, successCount.get());
        // All threads safely completed their serialized reset requests
        verify(userRepository, times(threadCount)).findByIdForUpdate(user.getId());
    }

    @Test
    @DisplayName("Request password reset fails closed and issues no token if user row cannot be locked")
    void requestPasswordResetLockedUserNotFoundFailsClosed() {
        User user = createTestUser("employee@flowdesk.test", "CurrentPass123!", UserStatus.ACTIVE);
        when(userRepository.findByEmailIgnoreCase("employee@flowdesk.test")).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.empty());

        ForgotPasswordResponse response = resetService.requestPasswordReset(
                new ForgotPasswordRequest("employee@flowdesk.test")
        );

        // Generic response preserved
        assertEquals(ForgotPasswordResponse.genericSuccess().message(), response.message());

        // Fail-closed: No token created, no token saved, no email dispatched, no audit log
        verify(tokenRepository, never()).save(any());
        verify(emailSender, never()).send(any());
        verify(auditService, never()).recordSystemAction(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Security notification delivery with ACCEPTED status logs success without error")
    void sendSecurityNotificationAccepted() {
        EmailMessage securityNotice = EmailMessage.of("employee@flowdesk.test", "Subject", "Body", "Html");
        when(emailTemplateService.buildSecurityNoticeEmail(any(), any(), any())).thenReturn(securityNotice);
        when(emailSender.send(any())).thenReturn(EmailDeliveryResult.accepted("msg-1", "employee@flowdesk.test"));

        assertDoesNotThrow(() -> resetService.sendSecurityNotificationSafely("employee@flowdesk.test"));
        verify(emailSender).send(securityNotice);
    }

    @Test
    @DisplayName("Security notification delivery with DISABLED status logs warning without throwing")
    void sendSecurityNotificationDisabled() {
        EmailMessage securityNotice = EmailMessage.of("employee@flowdesk.test", "Subject", "Body", "Html");
        when(emailTemplateService.buildSecurityNoticeEmail(any(), any(), any())).thenReturn(securityNotice);
        when(emailSender.send(any())).thenReturn(EmailDeliveryResult.disabled("employee@flowdesk.test"));

        assertDoesNotThrow(() -> resetService.sendSecurityNotificationSafely("employee@flowdesk.test"));
        verify(emailSender).send(securityNotice);
    }

    @Test
    @DisplayName("Security notification delivery with FAILED status logs warning without throwing")
    void sendSecurityNotificationFailed() {
        EmailMessage securityNotice = EmailMessage.of("employee@flowdesk.test", "Subject", "Body", "Html");
        when(emailTemplateService.buildSecurityNoticeEmail(any(), any(), any())).thenReturn(securityNotice);
        when(emailSender.send(any())).thenReturn(EmailDeliveryResult.failed("msg-2", "employee@flowdesk.test", "Connection timeout"));

        assertDoesNotThrow(() -> resetService.sendSecurityNotificationSafely("employee@flowdesk.test"));
        verify(emailSender).send(securityNotice);
    }

    @Test
    @DisplayName("Security notification delivery with Exception is swallowed safely without throwing")
    void sendSecurityNotificationExceptionSwallowedSafely() {
        EmailMessage securityNotice = EmailMessage.of("employee@flowdesk.test", "Subject", "Body", "Html");
        when(emailTemplateService.buildSecurityNoticeEmail(any(), any(), any())).thenReturn(securityNotice);
        doThrow(new RuntimeException("SMTP transport unreachable")).when(emailSender).send(any());

        assertDoesNotThrow(() -> resetService.sendSecurityNotificationSafely("employee@flowdesk.test"));
        verify(emailSender).send(securityNotice);
    }
}
