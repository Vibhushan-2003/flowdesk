package com.flowdesk.auth.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;
import com.flowdesk.auth.config.RefreshTokenProperties;
import com.flowdesk.auth.domain.RefreshSession;
import com.flowdesk.auth.repository.RefreshSessionRepository;
import com.flowdesk.auth.security.AuthenticatedUser;
import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class RefreshSessionService {

    private static final Logger log = LoggerFactory.getLogger(RefreshSessionService.class);
    private final SecureRandom secureRandom = new SecureRandom();

    private final RefreshSessionRepository refreshSessionRepository;
    private final RefreshTokenProperties properties;
    private final TokenService tokenService;
    private final AuditService auditService;

    public record TokenRefreshResult(
            String newRawToken,
            String newAccessToken,
            AuthenticatedUser user
    ) {}

    public RefreshSessionService(
            RefreshSessionRepository refreshSessionRepository,
            RefreshTokenProperties properties,
            TokenService tokenService,
            AuditService auditService
    ) {
        this.refreshSessionRepository = refreshSessionRepository;
        this.properties = properties;
        this.tokenService = tokenService;
        this.auditService = auditService;
    }

    /**
     * Creates a new root refresh session for a user upon login.
     */
    @Transactional
    public String createSession(User user, String clientIp, String userAgent) {
        Objects.requireNonNull(user, "user is required");

        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plus(properties.getIdleTtl());
        OffsetDateTime absoluteExpiresAt = now.plus(properties.getAbsoluteTtl());

        RefreshSession session = new RefreshSession(
                user,
                UUID.randomUUID(), // New family ID
                tokenHash,
                expiresAt,
                absoluteExpiresAt,
                sanitizeClientIp(clientIp),
                sanitizeUserAgent(userAgent)
        );

        refreshSessionRepository.save(session);
        return rawToken;
    }

    /**
     * Atomically rotates a refresh token, validating against expiry and replay attacks.
     */
    @Transactional
    public TokenRefreshResult rotateSession(String rawToken, String clientIp, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is missing");
        }

        String tokenHash = hashToken(rawToken);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        RefreshSession session = refreshSessionRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> {
                    log.warn("Attempt to refresh with unknown token hash");
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
                });

        User user = session.getUser();
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            session.revoke(now);
            refreshSessionRepository.save(session);
            log.warn("Refresh attempt for non-active or null user [{}]", user != null ? user.getEmail() : "null");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is unavailable");
        }

        // 1. Check if token was explicitly revoked
        if (session.isRevoked()) {
            log.warn("Attempt to refresh with revoked session [id={}, user={}]", session.getId(), user.getEmail());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has been revoked");
        }

        // 2. Check for token replay (already rotated)
        if (session.isReplaced()) {
            OffsetDateTime replacedAt = session.getReplacedAt();
            int gracePeriodSec = properties.getGracePeriodSeconds();

            if (replacedAt != null && now.isBefore(replacedAt.plusSeconds(gracePeriodSec))) {
                // Legitimate concurrent request within grace window (e.g. multiple tabs)
                log.info("Concurrent refresh within grace period for session family [{}]", session.getFamilyId());
                List<RefreshSession> activeSessions = refreshSessionRepository.findActiveInFamily(session.getFamilyId());
                if (!activeSessions.isEmpty()) {
                    RefreshSession activeSuccessor = activeSessions.get(0);
                    AuthenticatedUser authUser = toAuthenticatedUser(user);
                    String accessToken = tokenService.generateAccessToken(authUser);
                    // Do not rotate again, return empty newRawToken indicating cookie remains valid
                    return new TokenRefreshResult(null, accessToken, authUser);
                }
            }

            // Outside grace period: GENUINE REPLAY ATTACK! Revoke entire family
            log.error("GENUINE REFRESH TOKEN REPLAY DETECTED for family [{}] user [{}]. Revoking family.",
                    session.getFamilyId(), user.getEmail());

            refreshSessionRepository.revokeFamily(session.getFamilyId(), now);

            auditService.recordUserAction(
                    user,
                    AuditAction.SESSION_REPLAY_DETECTED,
                    AuditTargetType.USER,
                    user.getId(),
                    user.getEmail(),
                    Map.of(
                            "familyId", session.getFamilyId().toString(),
                            "compromisedSessionId", session.getId().toString(),
                            "clientIp", sanitizeClientIp(clientIp)
                    )
            );

            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session compromise detected; token family has been revoked");
        }

        // 3. Check expiration
        if (session.isExpired(now)) {
            session.revoke(now);
            refreshSessionRepository.save(session);
            log.info("Refresh token expired for user [{}]", user.getEmail());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has expired");
        }

        // 4. Check user status
        if (user.getStatus() != UserStatus.ACTIVE) {
            session.revoke(now);
            refreshSessionRepository.save(session);
            log.warn("Refresh attempt for non-active user [{}]", user.getEmail());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is unavailable");
        }

        // 5. Rotate: consume current token
        session.replace(now);
        refreshSessionRepository.save(session);

        // 6. Generate successor token
        String newRawToken = generateRawToken();
        String newTokenHash = hashToken(newRawToken);

        OffsetDateTime newExpiresAt = now.plus(properties.getIdleTtl());
        // Absolute expiry is strictly bounded by original session creation + absolute TTL
        OffsetDateTime absoluteExpiresAt = session.getAbsoluteExpiresAt();
        if (newExpiresAt.isAfter(absoluteExpiresAt)) {
            newExpiresAt = absoluteExpiresAt;
        }

        RefreshSession successor = new RefreshSession(
                user,
                session.getFamilyId(),
                newTokenHash,
                newExpiresAt,
                absoluteExpiresAt,
                sanitizeClientIp(clientIp),
                sanitizeUserAgent(userAgent)
        );
        refreshSessionRepository.save(successor);

        // 7. Generate fresh short-lived access JWT
        AuthenticatedUser authUser = toAuthenticatedUser(user);
        String accessToken = tokenService.generateAccessToken(authUser);

        auditService.recordUserAction(
                user,
                AuditAction.SESSION_REFRESHED,
                AuditTargetType.USER,
                user.getId(),
                user.getEmail(),
                Map.of(
                        "familyId", session.getFamilyId().toString(),
                        "newSessionId", successor.getId().toString(),
                        "clientIp", sanitizeClientIp(clientIp)
                )
        );

        return new TokenRefreshResult(newRawToken, accessToken, authUser);
    }

    /**
     * Revokes a session upon explicit logout.
     */
    @Transactional
    public void revokeSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String tokenHash = hashToken(rawToken);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        refreshSessionRepository.findByTokenHashForUpdate(tokenHash).ifPresent(session -> {
            session.revoke(now);
            refreshSessionRepository.save(session);

            auditService.recordUserAction(
                    session.getUser(),
                    AuditAction.SESSION_REVOKED,
                    AuditTargetType.USER,
                    session.getUser().getId(),
                    session.getUser().getEmail(),
                    Map.of(
                            "sessionId", session.getId().toString(),
                            "familyId", session.getFamilyId().toString()
                    )
            );
        });
    }

    /**
     * Revokes all active refresh sessions for a user (e.g. on password change or account deactivation).
     */
    @Transactional
    public void revokeAllSessionsForUser(UUID userId) {
        if (userId == null) return;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int revokedCount = refreshSessionRepository.revokeAllForUser(userId, now);
        log.info("Revoked [{}] active refresh sessions for user [{}]", revokedCount, userId);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm unavailable", e);
        }
    }

    private AuthenticatedUser toAuthenticatedUser(User user) {
        Set<com.flowdesk.user.domain.RoleCode> roleCodes = new HashSet<>();
        for (Role role : user.getRoles()) {
            roleCodes.add(role.getCode());
        }
        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                roleCodes,
                user.getStatus() == UserStatus.ACTIVE,
                user.getCredentialVersion()
        );
    }

    private String sanitizeClientIp(String ip) {
        if (ip == null || ip.isBlank()) return "unknown";
        String trimmed = ip.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    private String sanitizeUserAgent(String ua) {
        if (ua == null || ua.isBlank()) return "unknown";
        String trimmed = ua.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }
}
