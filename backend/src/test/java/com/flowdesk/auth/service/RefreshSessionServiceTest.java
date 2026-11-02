package com.flowdesk.auth.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.service.AuditService;
import com.flowdesk.auth.config.RefreshTokenProperties;
import com.flowdesk.auth.domain.RefreshSession;
import com.flowdesk.auth.repository.RefreshSessionRepository;
import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshSessionServiceTest {

    @Mock
    private RefreshSessionRepository refreshSessionRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private AuditService auditService;

    private RefreshTokenProperties properties;
    private RefreshSessionService refreshSessionService;

    private User testUser;
    private Role employeeRole;

    @BeforeEach
    void setUp() {
        properties = new RefreshTokenProperties();
        properties.setIdleTtl(Duration.ofDays(7));
        properties.setAbsoluteTtl(Duration.ofDays(30));
        properties.setGracePeriodSeconds(30);

        refreshSessionService = new RefreshSessionService(
                refreshSessionRepository,
                properties,
                tokenService,
                auditService
        );

        employeeRole = new Role(RoleCode.EMPLOYEE, "Employee");
        testUser = new User("john@example.com", "hashed_password", "John", "Doe");
        testUser.assignRole(employeeRole);
    }

    @Test
    @DisplayName("createSession generates a secure token and saves session with SHA-256 hash")
    void createSession_savesHashedSession() {
        String rawToken = refreshSessionService.createSession(testUser, "192.168.1.100", "Mozilla/5.0");

        assertThat(rawToken).isNotBlank();
        assertThat(rawToken).hasSizeGreaterThanOrEqualTo(48);

        ArgumentCaptor<RefreshSession> captor = ArgumentCaptor.forClass(RefreshSession.class);
        verify(refreshSessionRepository).save(captor.capture());

        RefreshSession saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(testUser);
        assertThat(saved.getTokenHash()).isEqualTo(RefreshSessionService.hashToken(rawToken));
        assertThat(saved.getFamilyId()).isNotNull();
        assertThat(saved.getClientIp()).isEqualTo("192.168.1.100");
        assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(saved.getExpiresAt()).isAfter(OffsetDateTime.now(ZoneOffset.UTC));
        assertThat(saved.getAbsoluteExpiresAt()).isAfter(saved.getExpiresAt());
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.isReplaced()).isFalse();
    }

    @Test
    @DisplayName("rotateSession succeeds, replaces previous session and creates successor")
    void rotateSession_success() {
        String rawToken = "initial-test-raw-token-123456789012345678901234";
        String tokenHash = RefreshSessionService.hashToken(rawToken);
        UUID familyId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        RefreshSession currentSession = new RefreshSession(
                testUser,
                familyId,
                tokenHash,
                now.plusDays(7),
                now.plusDays(30),
                "192.168.1.100",
                "Mozilla/5.0"
        );

        when(refreshSessionRepository.findByTokenHashForUpdate(tokenHash))
                .thenReturn(Optional.of(currentSession));
        when(tokenService.generateAccessToken(any())).thenReturn("new-access-jwt");

        RefreshSessionService.TokenRefreshResult result = refreshSessionService.rotateSession(
                rawToken, "192.168.1.101", "UpdatedBrowser"
        );

        assertThat(result.newAccessToken()).isEqualTo("new-access-jwt");
        assertThat(result.newRawToken()).isNotBlank();
        assertThat(result.newRawToken()).isNotEqualTo(rawToken);

        // Verify current session was replaced
        assertThat(currentSession.isReplaced()).isTrue();
        assertThat(currentSession.getReplacedAt()).isNotNull();

        // Verify successor session saved
        verify(refreshSessionRepository, times(2)).save(any(RefreshSession.class));
        verify(auditService).recordUserAction(
                eq(testUser),
                eq(AuditAction.SESSION_REFRESHED),
                any(),
                eq(testUser.getId()),
                eq(testUser.getEmail()),
                any()
        );
    }

    @Test
    @DisplayName("rotateSession rejects expired refresh token")
    void rotateSession_expired_throwsUnauthorized() {
        String rawToken = "expired-token-1234567890123456789012345678";
        String tokenHash = RefreshSessionService.hashToken(rawToken);
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);

        RefreshSession expiredSession = new RefreshSession(
                testUser,
                UUID.randomUUID(),
                tokenHash,
                past,
                past.plusDays(10),
                "127.0.0.1",
                "Browser"
        );

        when(refreshSessionRepository.findByTokenHashForUpdate(tokenHash))
                .thenReturn(Optional.of(expiredSession));

        assertThatThrownBy(() -> refreshSessionService.rotateSession(rawToken, "127.0.0.1", "Browser"))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.UNAUTHORIZED)
                .hasMessageContaining("Refresh token has expired");

        assertThat(expiredSession.isRevoked()).isTrue();
        verify(refreshSessionRepository).save(expiredSession);
    }

    @Test
    @DisplayName("rotateSession allows concurrent request within grace period without replay revocation")
    void rotateSession_concurrentWithinGracePeriod_returnsSuccessor() {
        String rawToken = "already-rotated-token-123456789012345678901234";
        String tokenHash = RefreshSessionService.hashToken(rawToken);
        UUID familyId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        RefreshSession session = new RefreshSession(
                testUser,
                familyId,
                tokenHash,
                now.plusDays(7),
                now.plusDays(30),
                "127.0.0.1",
                "Browser"
        );
        session.replace(now.minusSeconds(10)); // Replaced 10s ago (within 30s grace period)

        RefreshSession activeSuccessor = new RefreshSession(
                testUser,
                familyId,
                "successor-hash",
                now.plusDays(7),
                now.plusDays(30),
                "127.0.0.1",
                "Browser"
        );

        when(refreshSessionRepository.findByTokenHashForUpdate(tokenHash))
                .thenReturn(Optional.of(session));
        when(refreshSessionRepository.findActiveInFamily(familyId))
                .thenReturn(List.of(activeSuccessor));
        when(tokenService.generateAccessToken(any())).thenReturn("concurrent-access-token");

        RefreshSessionService.TokenRefreshResult result = refreshSessionService.rotateSession(
                rawToken, "127.0.0.1", "Browser"
        );

        assertThat(result.newAccessToken()).isEqualTo("concurrent-access-token");
        // newRawToken is null to indicate active cookie remains valid
        assertThat(result.newRawToken()).isNull();

        // Must NOT revoke the family
        verify(refreshSessionRepository, never()).revokeFamily(any(), any());
    }

    @Test
    @DisplayName("rotateSession detects genuine replay outside grace period and revokes entire family")
    void rotateSession_replayOutsideGracePeriod_revokesFamilyAndAudits() {
        String rawToken = "replayed-token-12345678901234567890123456789";
        String tokenHash = RefreshSessionService.hashToken(rawToken);
        UUID familyId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        RefreshSession session = new RefreshSession(
                testUser,
                familyId,
                tokenHash,
                now.plusDays(7),
                now.plusDays(30),
                "127.0.0.1",
                "Browser"
        );
        session.replace(now.minusSeconds(120)); // Replaced 120s ago (> 30s grace window)

        when(refreshSessionRepository.findByTokenHashForUpdate(tokenHash))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> refreshSessionService.rotateSession(rawToken, "attacker-ip", "AttackerUA"))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.UNAUTHORIZED)
                .hasMessageContaining("Session compromise detected");

        // Verify entire family revoked
        verify(refreshSessionRepository).revokeFamily(eq(familyId), any());

        // Verify security audit logged
        verify(auditService).recordUserAction(
                eq(testUser),
                eq(AuditAction.SESSION_REPLAY_DETECTED),
                any(),
                eq(testUser.getId()),
                eq(testUser.getEmail()),
                argThat(map -> map.containsKey("familyId") && "attacker-ip".equals(map.get("clientIp")))
        );
    }

    @Test
    @DisplayName("rotateSession rejects deactivated user")
    void rotateSession_deactivatedUser_throwsUnauthorized() {
        testUser.deactivate();
        String rawToken = "valid-token-for-inactive-user-12345678901234";
        String tokenHash = RefreshSessionService.hashToken(rawToken);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        RefreshSession session = new RefreshSession(
                testUser,
                UUID.randomUUID(),
                tokenHash,
                now.plusDays(7),
                now.plusDays(30),
                "127.0.0.1",
                "Browser"
        );

        when(refreshSessionRepository.findByTokenHashForUpdate(tokenHash))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> refreshSessionService.rotateSession(rawToken, "127.0.0.1", "Browser"))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.UNAUTHORIZED)
                .hasMessageContaining("Account is unavailable");

        assertThat(session.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("revokeSession revokes single session and records audit")
    void revokeSession_revokesAndAudits() {
        String rawToken = "token-to-revoke-12345678901234567890";
        String tokenHash = RefreshSessionService.hashToken(rawToken);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        RefreshSession session = new RefreshSession(
                testUser,
                UUID.randomUUID(),
                tokenHash,
                now.plusDays(7),
                now.plusDays(30),
                "127.0.0.1",
                "Browser"
        );

        when(refreshSessionRepository.findByTokenHashForUpdate(tokenHash))
                .thenReturn(Optional.of(session));

        refreshSessionService.revokeSession(rawToken);

        assertThat(session.isRevoked()).isTrue();
        verify(refreshSessionRepository).save(session);
        verify(auditService).recordUserAction(
                eq(testUser),
                eq(AuditAction.SESSION_REVOKED),
                any(),
                eq(testUser.getId()),
                eq(testUser.getEmail()),
                any()
        );
    }

    @Test
    @DisplayName("revokeAllSessionsForUser delegates to repository")
    void revokeAllSessionsForUser_delegates() {
        UUID userId = UUID.randomUUID();
        when(refreshSessionRepository.revokeAllForUser(eq(userId), any())).thenReturn(3);

        refreshSessionService.revokeAllSessionsForUser(userId);

        verify(refreshSessionRepository).revokeAllForUser(eq(userId), any());
    }

    @Test
    @DisplayName("hashToken generates consistent 64-character SHA-256 hex string")
    void hashToken_generatesConsistentHex() {
        String token = "flowdesk-sample-token";
        String hash1 = RefreshSessionService.hashToken(token);
        String hash2 = RefreshSessionService.hashToken(token);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
        assertThat(hash1).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("True concurrent refresh: multiple concurrent requests cannot create multiple active successors")
    void concurrentRefresh_cannotCreateMultipleSuccessors() throws Exception {
        String rawToken = "initial-concurrent-token-123456789012345678";
        String tokenHash = RefreshSessionService.hashToken(rawToken);
        UUID familyId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        RefreshSession initialSession = new RefreshSession(
                testUser,
                familyId,
                tokenHash,
                now.plusDays(7),
                now.plusDays(30),
                "127.0.0.1",
                "Browser"
        );

        RefreshSession activeSuccessor = new RefreshSession(
                testUser,
                familyId,
                "successor-hash-999",
                now.plusDays(7),
                now.plusDays(30),
                "127.0.0.1",
                "Browser"
        );

        when(tokenService.generateAccessToken(any())).thenReturn("valid-jwt");
        when(refreshSessionRepository.findActiveInFamily(familyId)).thenReturn(List.of(activeSuccessor));

        // Thread 1 performs initial rotation
        when(refreshSessionRepository.findByTokenHashForUpdate(tokenHash))
                .thenReturn(Optional.of(initialSession));

        RefreshSessionService.TokenRefreshResult res1 = refreshSessionService.rotateSession(rawToken, "127.0.0.1", "Browser");
        assertThat(res1.newRawToken()).isNotNull();
        assertThat(initialSession.isReplaced()).isTrue();

        // Thread 2 calls immediately within grace period with the old token
        RefreshSessionService.TokenRefreshResult res2 = refreshSessionService.rotateSession(rawToken, "127.0.0.1", "Browser");
        // Must NOT create another successor token; raw token is null
        assertThat(res2.newRawToken()).isNull();
        assertThat(res2.newAccessToken()).isEqualTo("valid-jwt");

        // Successor was created exactly ONCE across both requests
        verify(refreshSessionRepository, times(2)).save(any(RefreshSession.class)); // initial replace + 1 successor
    }
}
