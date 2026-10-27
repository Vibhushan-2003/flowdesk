package com.flowdesk.auth.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;
import com.flowdesk.auth.config.GoogleOAuthProperties;
import com.flowdesk.auth.domain.UserExternalIdentity;
import com.flowdesk.auth.domain.UserPendingOAuthLink;
import com.flowdesk.auth.dto.GoogleAuthRequest;
import com.flowdesk.auth.dto.GoogleOAuthConfigResponse;
import com.flowdesk.auth.dto.GoogleUserInfo;
import com.flowdesk.auth.dto.LoginResponse;
import com.flowdesk.auth.exception.OAuthLinkingInvalidPasswordException;
import com.flowdesk.auth.exception.OAuthLinkingRequiredException;
import com.flowdesk.auth.repository.UserExternalIdentityRepository;
import com.flowdesk.auth.repository.UserPendingOAuthLinkRepository;
import com.flowdesk.auth.security.AuthenticatedUser;
import com.flowdesk.auth.security.GoogleTokenVerifier;
import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.repository.UserRepository;
import com.flowdesk.user.service.UserInvitationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthService.class);
    private static final String PROVIDER_GOOGLE = "GOOGLE";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration LINK_TOKEN_TTL = Duration.ofMinutes(10);
    private static final int MAX_FAILED_LINK_ATTEMPTS = 5;

    private final GoogleOAuthProperties properties;
    private final GoogleTokenVerifier tokenVerifier;
    private final UserExternalIdentityRepository externalIdentityRepository;
    private final UserPendingOAuthLinkRepository pendingLinkRepository;
    private final UserRepository userRepository;
    private final UserInvitationService userInvitationService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public GoogleAuthService(
            GoogleOAuthProperties properties,
            GoogleTokenVerifier tokenVerifier,
            UserExternalIdentityRepository externalIdentityRepository,
            UserPendingOAuthLinkRepository pendingLinkRepository,
            UserRepository userRepository,
            UserInvitationService userInvitationService,
            TokenService tokenService,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.tokenVerifier = Objects.requireNonNull(tokenVerifier, "tokenVerifier must not be null");
        this.externalIdentityRepository = Objects.requireNonNull(externalIdentityRepository, "externalIdentityRepository must not be null");
        this.pendingLinkRepository = Objects.requireNonNull(pendingLinkRepository, "pendingLinkRepository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.userInvitationService = Objects.requireNonNull(userInvitationService, "userInvitationService must not be null");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
    }

    public GoogleOAuthConfigResponse getOAuthConfig() {
        return new GoogleOAuthConfigResponse(
                properties.isEnabled(),
                properties.getClientId(),
                properties.getRedirectUri(),
                properties.getAuthUri()
        );
    }

    @Transactional(noRollbackFor = {
            OAuthLinkingRequiredException.class,
            OAuthLinkingInvalidPasswordException.class
    })
    public LoginResponse authenticate(GoogleAuthRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        // If caller is submitting a pending linking token with password, delegate to completeLinking
        if (request.linkToken() != null && !request.linkToken().isBlank()) {
            return completeLinking(request.linkToken(), request.flowDeskPassword());
        }

        GoogleUserInfo userInfo = resolveGoogleUser(request);

        if (!userInfo.emailVerified()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google email address is not verified");
        }

        String normalizedEmail = userInfo.email().trim().toLowerCase(Locale.ROOT);

        // 1. Check if this Google subject is already linked to an existing FlowDesk user
        Optional<UserExternalIdentity> linkedOpt = externalIdentityRepository
                .findByProviderAndProviderSubject(PROVIDER_GOOGLE, userInfo.sub());

        if (linkedOpt.isPresent()) {
            User user = linkedOpt.get().getUser();
            return loginLinkedUser(user, userInfo);
        }

        // 2. Google subject is not linked yet.
        // Check if caller presented an invitation token (Invitee onboarding path)
        if (request.invitationToken() != null && !request.invitationToken().isBlank()) {
            User activatedUser = userInvitationService.acceptInvitationWithGoogle(request.invitationToken());

            UserExternalIdentity identity = new UserExternalIdentity(
                    activatedUser,
                    PROVIDER_GOOGLE,
                    userInfo.sub(),
                    normalizedEmail
            );
            externalIdentityRepository.save(identity);

            auditService.recordSystemAction(
                    AuditAction.USER_OAUTH_LINKED,
                    AuditTargetType.USER,
                    activatedUser.getId(),
                    activatedUser.getEmail(),
                    Map.of("provider", PROVIDER_GOOGLE, "subject", userInfo.sub())
            );

            log.info("Google identity [{}] linked to invited user [{}] during invitation acceptance.",
                    userInfo.sub(), activatedUser.getEmail());

            return toLoginResponse(activatedUser);
        }

        // 3. Subject is not linked and no invitation token provided.
        // Look up account by email to apply strict account-linking policy.
        Optional<User> existingUserOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);

        if (existingUserOpt.isEmpty()) {
            // Uninvited user attempting Google login -> reject (prevents bypassing invitation system)
            log.warn("Rejected uninvited Google login attempt for email [{}] (sub=[{}])",
                    normalizedEmail, userInfo.sub());
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your Google account (" + normalizedEmail + ") is not registered with FlowDesk. Please contact an administrator to receive an invitation."
            );
        }

        User existingUser = existingUserOpt.get();

        if (existingUser.getStatus() == UserStatus.INACTIVE) {
            log.warn("Rejected Google login for deactivated user [{}]", normalizedEmail);
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account has been deactivated. Please contact an administrator."
            );
        }

        if (existingUser.getStatus() == UserStatus.INVITED) {
            log.warn("Rejected Google login for invited user [{}] without invitation token", normalizedEmail);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This account has an outstanding invitation. Please use your invitation link to activate and link your Google account."
            );
        }

        // Existing ACTIVE user: initiate single-use pending linking transaction.
        // The Google authorization code was exchanged ONCE. We now generate a short-lived,
        // cryptographically secure linking token bound to the verified Google subject and user.
        String rawLinkToken = generateSecureLinkToken();
        String tokenHash = hashToken(rawLinkToken);
        Instant expiresAt = Instant.now().plus(LINK_TOKEN_TTL);

        UserPendingOAuthLink pendingLink = new UserPendingOAuthLink(
                existingUser,
                PROVIDER_GOOGLE,
                userInfo.sub(),
                normalizedEmail,
                tokenHash,
                expiresAt
        );
        pendingLinkRepository.save(pendingLink);

        log.info("Created pending OAuth linking transaction for user [{}] (sub=[{}], expiresAt=[{}])",
                existingUser.getEmail(), userInfo.sub(), expiresAt);

        throw new OAuthLinkingRequiredException(rawLinkToken, normalizedEmail, PROVIDER_GOOGLE);
    }

    @Transactional(noRollbackFor = OAuthLinkingInvalidPasswordException.class)
    public LoginResponse completeLinking(String rawLinkToken, String flowDeskPassword) {
        if (rawLinkToken == null || rawLinkToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Linking token is required.");
        }
        if (flowDeskPassword == null || flowDeskPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required to confirm account linking.");
        }

        String tokenHash = hashToken(rawLinkToken.trim());

        // Pessimistic write lock on pending link row ensures atomic consumption and replay protection
        UserPendingOAuthLink pendingLink = pendingLinkRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired linking transaction."));

        if (pendingLink.isConsumed()) {
            log.warn("Rejected attempt to reuse already-consumed linking transaction for email [{}]", pendingLink.getEmail());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This linking transaction has already been used. Please sign in with Google again.");
        }

        if (pendingLink.isExpired()) {
            log.warn("Rejected expired linking transaction for email [{}]", pendingLink.getEmail());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This linking transaction has expired. Please sign in with Google again.");
        }

        if (pendingLink.getFailedAttempts() >= MAX_FAILED_LINK_ATTEMPTS) {
            log.warn("Rejected linking transaction with exceeded attempts for email [{}]", pendingLink.getEmail());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many failed password attempts. This linking transaction has been invalidated.");
        }

        // Acquire lock on user row
        User user = userRepository.findByIdForUpdate(pendingLink.getUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Associated user account could not be found."));

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Rejected linking attempt for non-active user [{}] (status=[{}])", user.getEmail(), user.getStatus());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account has been deactivated. Please contact an administrator.");
        }

        // Verify password against user password hash
        if (!passwordEncoder.matches(flowDeskPassword, user.getPasswordHash())) {
            pendingLink.incrementFailedAttempts();
            boolean invalidated = pendingLink.getFailedAttempts() >= MAX_FAILED_LINK_ATTEMPTS;
            if (invalidated) {
                pendingLink.markConsumed(); // Invalidate permanently
                pendingLinkRepository.save(pendingLink);
                log.warn("Linking transaction invalidated after {} failed password attempts for user [{}]",
                        MAX_FAILED_LINK_ATTEMPTS, user.getEmail());
                throw new OAuthLinkingInvalidPasswordException(0, true);
            }

            pendingLinkRepository.save(pendingLink);
            int remaining = MAX_FAILED_LINK_ATTEMPTS - pendingLink.getFailedAttempts();
            log.warn("Invalid password attempt for linking user [{}]. Attempts remaining: {}", user.getEmail(), remaining);
            throw new OAuthLinkingInvalidPasswordException(remaining, false);
        }

        // Successful password verification: consume linking transaction atomically
        pendingLink.markConsumed();
        pendingLinkRepository.save(pendingLink);

        // Check if identity is already linked to prevent duplicate insertion
        if (!externalIdentityRepository.existsByProviderAndProviderSubject(pendingLink.getProvider(), pendingLink.getProviderSubject())) {
            UserExternalIdentity identity = new UserExternalIdentity(
                    user,
                    pendingLink.getProvider(),
                    pendingLink.getProviderSubject(),
                    pendingLink.getEmail()
            );
            externalIdentityRepository.save(identity);
        }

        auditService.recordUserAction(
                user,
                AuditAction.USER_OAUTH_LINKED,
                AuditTargetType.USER,
                user.getId(),
                user.getEmail(),
                Map.of("provider", pendingLink.getProvider(), "subject", pendingLink.getProviderSubject())
        );

        log.info("Google identity [{}] successfully linked to existing user [{}] via verified linking transaction.",
                pendingLink.getProviderSubject(), user.getEmail());

        return toLoginResponse(user);
    }

    private LoginResponse loginLinkedUser(User user, GoogleUserInfo userInfo) {
        if (user.getStatus() == UserStatus.INACTIVE) {
            log.warn("Rejected Google login for linked inactive user [{}]", user.getEmail());
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account has been deactivated. Please contact an administrator."
            );
        }

        if (user.getStatus() == UserStatus.INVITED) {
            log.warn("Rejected Google login for linked invited user [{}]", user.getEmail());
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Your account is pending invitation acceptance. Please accept your invitation first."
            );
        }

        log.info("Google login successful for linked user [{}] (sub=[{}])", user.getEmail(), userInfo.sub());
        return toLoginResponse(user);
    }

    private GoogleUserInfo resolveGoogleUser(GoogleAuthRequest request) {
        if (request.code() != null && !request.code().isBlank()) {
            if (request.state() == null || request.state().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth state parameter is required");
            }
            return tokenVerifier.exchangeCodeAndVerify(
                    request.code(),
                    properties.getRedirectUri(),
                    request.codeVerifier()
            );
        }

        if (request.idToken() != null && !request.idToken().isBlank()) {
            return tokenVerifier.verifyIdToken(request.idToken());
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Either Google authorization code or ID token must be provided");
    }

    private String generateSecureLinkToken() {
        byte[] entropy = new byte[32];
        SECURE_RANDOM.nextBytes(entropy);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
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

    private LoginResponse toLoginResponse(User user) {
        Set<RoleCode> roleCodes = user.getRoles()
                .stream()
                .map(Role::getCode)
                .collect(Collectors.toSet());

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                roleCodes,
                user.getStatus() == UserStatus.ACTIVE,
                user.getCredentialVersion()
        );

        String accessToken = tokenService.generateAccessToken(authenticatedUser);

        return new LoginResponse(
                accessToken,
                "Bearer",
                user.getId(),
                user.getEmail(),
                roleCodes
        );
    }
}
