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
import com.flowdesk.email.model.EmailDeliveryResult;
import com.flowdesk.email.model.EmailDeliveryStatus;
import com.flowdesk.email.model.EmailMessage;
import com.flowdesk.email.service.EmailSender;
import com.flowdesk.email.template.EmailTemplateService;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final EmailTemplateService emailTemplateService;
    private final PasswordResetProperties properties;
    private final AuditService auditService;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            EmailTemplateService emailTemplateService,
            PasswordResetProperties properties,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.emailTemplateService = emailTemplateService;
        this.properties = properties;
        this.auditService = auditService;
    }

    /**
     * Initiates a password reset request.
     * <p>
     * Returns a generic response in all cases to prevent account enumeration.
     * Serializes concurrent requests per user using a pessimistic user row lock.
     */
    @Transactional
    public ForgotPasswordResponse requestPasswordReset(ForgotPasswordRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String maskedEmail = EmailMessage.maskEmail(normalizedEmail);

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);

        if (userOpt.isEmpty()) {
            log.info("Password reset requested for non-existent account [{}]. Generic response returned.", maskedEmail);
            return ForgotPasswordResponse.genericSuccess();
        }

        // Lock user row with SELECT ... FOR UPDATE to serialize concurrent issuance per user
        Optional<User> lockedUserOpt = userRepository.findByIdForUpdate(userOpt.get().getId());
        if (lockedUserOpt.isEmpty()) {
            log.warn("Password reset requested but user row could not be locked for [{}] (record unavailable).", maskedEmail);
            return ForgotPasswordResponse.genericSuccess();
        }

        User user = lockedUserOpt.get();

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("Password reset requested for inactive account [{}]. Generic response returned.", maskedEmail);
            return ForgotPasswordResponse.genericSuccess();
        }

        Instant now = Instant.now();

        // Invalidate any previously issued active tokens for this user
        tokenRepository.invalidateActiveTokensForUser(user.getId(), now);

        // Generate cryptographically secure 256-bit entropy token
        byte[] entropy = new byte[properties.getTokenEntropyBytes()];
        SECURE_RANDOM.nextBytes(entropy);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        String tokenHash = hashToken(rawToken);

        Instant expiresAt = now.plus(properties.getTokenValidityDuration());
        PasswordResetToken token = new PasswordResetToken(user, tokenHash, expiresAt);
        tokenRepository.save(token);

        // Build reset link using configured frontend origin (never derived from request headers)
        String resetLink = properties.getFrontendOrigin() + "/reset-password?token=" + rawToken;

        EmailMessage emailMessage = emailTemplateService.buildPasswordResetEmail(
                user.getEmail(),
                resetLink,
                properties.getTokenValidityDuration()
        );

        EmailDeliveryResult deliveryResult;
        try {
            deliveryResult = emailSender.send(emailMessage);
        } catch (Exception ex) {
            log.error("Outbound email delivery exception for [{}]: {}", maskedEmail, ex.getMessage());
            // Invalidate the token immediately so a failed delivery leaves no active token
            token.invalidate(now);
            tokenRepository.save(token);
            return ForgotPasswordResponse.genericSuccess();
        }

        if (deliveryResult == null || deliveryResult.status() != EmailDeliveryStatus.ACCEPTED) {
            String statusName = deliveryResult != null && deliveryResult.status() != null
                    ? deliveryResult.status().name()
                    : "UNKNOWN";
            log.warn("Password reset email was not accepted for [{}] (status={}). Invalidating token.",
                    maskedEmail, statusName);
            // Ensure failed or disabled delivery leaves no usable reset token
            token.invalidate(now);
            tokenRepository.save(token);
            return ForgotPasswordResponse.genericSuccess();
        }

        // Delivery ACCEPTED by SMTP transport
        log.info("Password reset email dispatched to [{}] (expires in {} minutes).",
                maskedEmail, properties.getTokenValidityMinutes());

        auditService.recordSystemAction(
                AuditAction.PASSWORD_RESET_REQUESTED,
                AuditTargetType.USER,
                user.getId(),
                user.getEmail(),
                Map.of("expiresAt", expiresAt.toString())
        );

        // Always return the exact same generic success response to prevent account enumeration
        return ForgotPasswordResponse.genericSuccess();
    }

    /**
     * Completes password reset using a valid, unexpired, single-use token.
     * <p>
     * Adheres to consistent global lock ordering (users row FIRST, password_reset_tokens row SECOND)
     * to prevent deadlocks. Security notifications are dispatched only after the transaction commits.
     */
    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        if (request.token() == null || request.token().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset token is required");
        }

        if (request.newPassword() == null || request.newPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password is required");
        }

        if (request.confirmPassword() == null || !request.newPassword().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match");
        }

        if (request.newPassword().length() < 8 || request.newPassword().length() > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be between 8 and 72 characters");
        }

        String tokenHash = hashToken(request.token().trim());
        Instant now = Instant.now();

        // 1. Initial lookup to discover the owning user
        PasswordResetToken tokenPreview = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired password reset token"));

        // 2. Global lock hierarchy: Always lock users row FIRST
        User user = userRepository.findByIdForUpdate(tokenPreview.getUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is unavailable"));

        // 3. Lock token row under the user lock SECOND
        PasswordResetToken token = tokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired password reset token"));

        if (!token.isValid(now)) {
            log.warn("Attempt to reset password with invalid/expired token [id={}]", token.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired password reset token");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Attempt to reset password for inactive user [id={}]", user.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is unavailable");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password cannot be the same as current password");
        }

        // Single-use token consumption
        token.consume(now);
        tokenRepository.save(token);

        // Invalidate any other outstanding tokens for this user
        tokenRepository.invalidateActiveTokensForUser(user.getId(), now);

        // Update password and increment credential_version (revoking all existing JWTs)
        String encodedNewPassword = passwordEncoder.encode(request.newPassword());
        user.changePassword(encodedNewPassword);
        userRepository.save(user);

        log.info("Password successfully reset for user [{}]. All existing JWT sessions invalidated.",
                EmailMessage.maskEmail(user.getEmail()));

        auditService.recordSystemAction(
                AuditAction.PASSWORD_RESET_COMPLETED,
                AuditTargetType.USER,
                user.getId(),
                user.getEmail(),
                Map.of("credentialVersion", user.getCredentialVersion())
        );

        // Register post-commit notification: security email is only dispatched AFTER database commit succeeds
        registerAfterCommitNotification(user.getEmail());

        return ResetPasswordResponse.defaultSuccess();
    }

    private void registerAfterCommitNotification(String recipientEmail) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendSecurityNotificationSafely(recipientEmail);
                }
            });
        } else {
            // Non-transactional fallback (e.g. mock test environments without transaction manager)
            sendSecurityNotificationSafely(recipientEmail);
        }
    }

    void sendSecurityNotificationSafely(String recipientEmail) {
        String maskedEmail = EmailMessage.maskEmail(recipientEmail);
        try {
            EmailMessage securityNotice = emailTemplateService.buildSecurityNoticeEmail(
                    recipientEmail,
                    "Password Reset Successfully",
                    "The password for your FlowDesk account was recently reset. If you did not perform this action, please contact your administrator immediately."
            );
            EmailDeliveryResult deliveryResult = emailSender.send(securityNotice);

            if (deliveryResult != null && deliveryResult.status() == EmailDeliveryStatus.ACCEPTED) {
                log.info("Password change security notice successfully accepted by SMTP transport for [{}].",
                        maskedEmail);
            } else {
                String statusName = (deliveryResult != null && deliveryResult.status() != null)
                        ? deliveryResult.status().name()
                        : "UNKNOWN";
                log.warn("Password change security notice was not accepted for [{}] (status={}).",
                        maskedEmail, statusName);
            }
        } catch (Exception ex) {
            log.warn("Unable to dispatch password change security notice to [{}]: {}",
                    maskedEmail, ex.getMessage());
        }
    }

    /**
     * Hashes a raw token with SHA-256 and outputs a 64-character lowercase hex string.
     */
    public static String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Token must not be blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 message digest not available", e);
        }
    }

    /**
     * Purges expired or consumed reset tokens older than the retention threshold.
     * Guarantees active, unexpired tokens are never deleted.
     */
    @Transactional
    public int cleanupOldTokens(Duration retention) {
        Instant now = Instant.now();
        Duration safeRetention = (retention != null && !retention.isNegative()) ? retention : Duration.ofDays(7);
        Instant cutoff = now.minus(safeRetention);
        return tokenRepository.deleteTokensOlderThan(cutoff, now);
    }
}
