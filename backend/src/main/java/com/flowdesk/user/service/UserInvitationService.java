package com.flowdesk.user.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;
import com.flowdesk.auth.dto.AcceptInvitationRequest;
import com.flowdesk.auth.dto.AcceptInvitationResponse;
import com.flowdesk.email.model.EmailDeliveryResult;
import com.flowdesk.email.model.EmailDeliveryStatus;
import com.flowdesk.email.model.EmailMessage;
import com.flowdesk.email.service.EmailSender;
import com.flowdesk.email.template.EmailTemplateService;
import com.flowdesk.user.config.UserInvitationProperties;
import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserInvitation;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.dto.CreateUserInvitationRequest;
import com.flowdesk.user.dto.ResendInvitationResponse;
import com.flowdesk.user.dto.UserResponse;
import com.flowdesk.user.exception.DuplicateEmailException;
import com.flowdesk.user.repository.RoleRepository;
import com.flowdesk.user.repository.UserInvitationRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserInvitationService {

    private static final Logger log = LoggerFactory.getLogger(UserInvitationService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserInvitationRepository userInvitationRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final EmailTemplateService emailTemplateService;
    private final UserInvitationProperties properties;
    private final AuditService auditService;

    public UserInvitationService(
            UserRepository userRepository,
            UserInvitationRepository userInvitationRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            EmailTemplateService emailTemplateService,
            UserInvitationProperties properties,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.userInvitationRepository = userInvitationRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.emailTemplateService = emailTemplateService;
        this.properties = properties;
        this.auditService = auditService;
    }

    /**
     * Issues an admin-created invitation to a new user account.
     * Transactionally saves the invited user, roles, hashed invitation token, and audit record.
     * Dispatches the invitation email via transaction after-commit synchronization.
     */
    @Transactional
    public UserResponse createInvitation(CreateUserInvitationRequest request, UUID actorUserId) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateEmailException(normalizedEmail);
        }

        User actorUser = userRepository.findWithRolesById(actorUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated administrator not found"));

        List<Role> resolvedRoles = roleRepository.findByCodeIn(request.roles());
        if (resolvedRoles.size() != request.roles().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more specified roles are invalid");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getTokenValidityDuration());

        // Generate un-usable initial credential representation satisfying NOT NULL
        String unusablePasswordHash = generateUnusablePasswordHash();

        User user = User.createInvitedUser(
                normalizedEmail,
                request.firstName().trim(),
                request.lastName().trim(),
                unusablePasswordHash
        );
        user.replaceRoles(new HashSet<>(resolvedRoles));
        User savedUser = userRepository.saveAndFlush(user);

        // Generate cryptographically secure token (>= 256 bits of entropy)
        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        UserInvitation invitation = new UserInvitation(savedUser, tokenHash, expiresAt);
        userInvitationRepository.save(invitation);

        List<String> assignedRoleNames = request.roles()
                .stream()
                .map(RoleCode::name)
                .sorted()
                .toList();

        auditService.recordUserAction(
                actorUser,
                AuditAction.USER_INVITED,
                AuditTargetType.USER,
                savedUser.getId(),
                savedUser.getEmail(),
                Map.of(
                        "assignedRoles", assignedRoleNames,
                        "expiresAt", expiresAt.toString()
                )
        );

        // Build invitation link using trusted configured frontend origin
        String invitationUrl = properties.getFrontendOrigin() + "/accept-invitation?token=" + rawToken;
        String inviterName = actorUser.getFirstName() + " " + actorUser.getLastName();

        registerAfterCommitEmail(
                savedUser.getEmail(),
                inviterName,
                invitationUrl,
                properties.getTokenValidityDuration()
        );

        return toResponse(savedUser);
    }

    /**
     * Resends an invitation for an existing INVITED user.
     * Revokes any unconsumed previous token to enforce the single-unconsumed-invitation constraint.
     */
    @Transactional
    public ResendInvitationResponse resendInvitation(UUID targetUserId, UUID actorUserId) {
        Objects.requireNonNull(targetUserId, "targetUserId must not be null");
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");

        // Lock user row first to serialize concurrent reissue requests
        User targetUser = userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (targetUser.getStatus() != UserStatus.INVITED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot resend invitation: user is not in INVITED status"
            );
        }

        User actorUser = userRepository.findWithRolesById(actorUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated administrator not found"));

        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getTokenValidityDuration());

        // Invalidate previous unconsumed invitations (revocation & partial unique index safety)
        userInvitationRepository.invalidateActiveInvitationsForUser(targetUser.getId(), now);

        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        UserInvitation newInvitation = new UserInvitation(targetUser, tokenHash, expiresAt);
        userInvitationRepository.save(newInvitation);

        auditService.recordUserAction(
                actorUser,
                AuditAction.USER_INVITATION_RESENT,
                AuditTargetType.USER,
                targetUser.getId(),
                targetUser.getEmail(),
                Map.of("expiresAt", expiresAt.toString())
        );

        String invitationUrl = properties.getFrontendOrigin() + "/accept-invitation?token=" + rawToken;
        String inviterName = actorUser.getFirstName() + " " + actorUser.getLastName();

        registerAfterCommitEmail(
                targetUser.getEmail(),
                inviterName,
                invitationUrl,
                properties.getTokenValidityDuration()
        );

        return new ResendInvitationResponse("Invitation resent successfully");
    }

    /**
     * Public acceptance of an invitation token.
     * Enforces strict lock ordering (users row FIRST, user_invitations row SECOND) to prevent deadlocks
     * and guarantees atomic credential setup, credential version bump, user activation, and token consumption.
     */
    @Transactional
    public AcceptInvitationResponse acceptInvitation(AcceptInvitationRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        if (request.token() == null || request.token().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation token is required");
        }

        if (request.newPassword() == null || request.newPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }

        if (request.confirmPassword() == null || !request.newPassword().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match");
        }

        if (request.newPassword().length() < 8 || request.newPassword().length() > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be between 8 and 72 characters");
        }

        String tokenHash = hashToken(request.token().trim());
        Instant now = Instant.now();

        // Initial lookup to determine target user
        Optional<UserInvitation> initialOpt = userInvitationRepository.findByTokenHash(tokenHash);
        if (initialOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired invitation token");
        }

        UUID userId = initialOpt.get().getUser().getId();

        // 1. Acquire pessimistic lock on users row FIRST
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is unavailable"));

        // 2. Acquire pessimistic lock on user_invitations row SECOND
        UserInvitation invitation = userInvitationRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired invitation token"));

        // Validate state under lock
        if (invitation.isConsumed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation token has already been used");
        }

        if (invitation.isExpired(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation token has expired");
        }

        if (user.getStatus() != UserStatus.INVITED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is not in invited status");
        }

        String encodedPassword = passwordEncoder.encode(request.newPassword());
        user.acceptInvitation(encodedPassword);
        userRepository.save(user);

        invitation.consume(now);
        userInvitationRepository.save(invitation);

        auditService.recordSystemAction(
                AuditAction.USER_INVITATION_ACCEPTED,
                AuditTargetType.USER,
                user.getId(),
                user.getEmail(),
                Map.of("credentialVersion", user.getCredentialVersion())
        );

        log.info("Invitation accepted successfully for account [{}]", EmailMessage.maskEmail(user.getEmail()));

        return AcceptInvitationResponse.defaultSuccess();
    }

    /**
     * Accepts an invitation using a verified Google OAuth identity, activating the account
     * without bypassing the required invitation token.
     * Enforces the same strict lock ordering (user row FIRST, invitation row SECOND).
     */
    @Transactional
    public User acceptInvitationWithGoogle(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken must not be null");

        String tokenHash = hashToken(rawToken.trim());
        Instant now = Instant.now();

        Optional<UserInvitation> initialOpt = userInvitationRepository.findByTokenHash(tokenHash);
        if (initialOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired invitation token");
        }

        UUID userId = initialOpt.get().getUser().getId();

        // 1. Lock user row FIRST
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is unavailable"));

        // 2. Lock invitation row SECOND
        UserInvitation invitation = userInvitationRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired invitation token"));

        if (invitation.isConsumed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation token has already been used");
        }

        if (invitation.isExpired(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation token has expired");
        }

        if (user.getStatus() != UserStatus.INVITED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is not in invited status");
        }

        String unusableHash = generateUnusablePasswordHash();
        user.acceptInvitation(unusableHash);
        userRepository.save(user);

        invitation.consume(now);
        userInvitationRepository.save(invitation);

        auditService.recordSystemAction(
                AuditAction.USER_INVITATION_ACCEPTED,
                AuditTargetType.USER,
                user.getId(),
                user.getEmail(),
                Map.of("authProvider", "GOOGLE", "credentialVersion", user.getCredentialVersion())
        );

        log.info("Invitation accepted via Google OAuth successfully for account [{}]", EmailMessage.maskEmail(user.getEmail()));

        return user;
    }

    @Transactional
    public int cleanupOldInvitations(Duration cutoffDuration) {
        Instant cutoff = Instant.now().minus(cutoffDuration);
        return userInvitationRepository.deleteOldInvitations(cutoff);
    }

    private void registerAfterCommitEmail(
            String recipientEmail,
            String inviterName,
            String setupUrl,
            Duration expiration
    ) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendInvitationEmailSafely(recipientEmail, inviterName, setupUrl, expiration);
                }
            });
        } else {
            sendInvitationEmailSafely(recipientEmail, inviterName, setupUrl, expiration);
        }
    }

    void sendInvitationEmailSafely(
            String recipientEmail,
            String inviterName,
            String setupUrl,
            Duration expiration
    ) {
        String maskedEmail = EmailMessage.maskEmail(recipientEmail);
        try {
            EmailMessage message = emailTemplateService.buildUserInvitationEmail(
                    recipientEmail,
                    inviterName,
                    setupUrl,
                    expiration
            );
            EmailDeliveryResult result = emailSender.send(message);

            if (result != null && result.status() == EmailDeliveryStatus.ACCEPTED) {
                log.info("Invitation email successfully accepted by SMTP transport for [{}].", maskedEmail);
            } else {
                String statusName = (result != null && result.status() != null)
                        ? result.status().name()
                        : "UNKNOWN";
                log.warn("Invitation email was not accepted for [{}] (status={}). Invitation remains recoverable via admin resend.",
                        maskedEmail, statusName);
            }
        } catch (Exception ex) {
            log.warn("Outbound invitation email delivery exception for [{}]: {}. Invitation remains recoverable via admin resend.",
                    maskedEmail, ex.getMessage());
        }
    }

    private String generateSecureToken() {
        byte[] entropy = new byte[properties.getTokenEntropyBytes()];
        SECURE_RANDOM.nextBytes(entropy);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
    }

    private String generateUnusablePasswordHash() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return "!INVITED!" + UUID.randomUUID().toString().replace("-", "") + HexFormat.of().formatHex(randomBytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private UserResponse toResponse(User user) {
        Set<RoleCode> roles = user.getRoles()
                .stream()
                .map(Role::getCode)
                .collect(Collectors.toSet());

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus(),
                roles,
                user.getCreatedAt()
        );
    }
}
