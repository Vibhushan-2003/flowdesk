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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock
    private GoogleOAuthProperties properties;

    @Mock
    private GoogleTokenVerifier tokenVerifier;

    @Mock
    private UserExternalIdentityRepository externalIdentityRepository;

    @Mock
    private UserPendingOAuthLinkRepository pendingLinkRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserInvitationService userInvitationService;

    @Mock
    private TokenService tokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    private GoogleAuthService googleAuthService;

    @BeforeEach
    void setUp() {
        googleAuthService = new GoogleAuthService(
                properties,
                tokenVerifier,
                externalIdentityRepository,
                pendingLinkRepository,
                userRepository,
                userInvitationService,
                tokenService,
                passwordEncoder,
                auditService
        );
    }

    private User buildUser(String email, String firstName, String lastName) {
        User user = new User(email, "$2a$10$hashedPassword", firstName, lastName);
        user.assignRole(new Role(RoleCode.EMPLOYEE, "Employee"));
        return user;
    }

    private String hashToken(String rawToken) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    @Test
    @DisplayName("getOAuthConfig returns configured properties")
    void getOAuthConfig_returnsConfig() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getClientId()).thenReturn("test-client-id.apps.googleusercontent.com");
        when(properties.getRedirectUri()).thenReturn("http://localhost:5173/auth/google/callback");
        when(properties.getAuthUri()).thenReturn("https://accounts.google.com/o/oauth2/v2/auth");

        GoogleOAuthConfigResponse config = googleAuthService.getOAuthConfig();

        assertThat(config.enabled()).isTrue();
        assertThat(config.clientId()).isEqualTo("test-client-id.apps.googleusercontent.com");
        assertThat(config.redirectUri()).isEqualTo("http://localhost:5173/auth/google/callback");
        assertThat(config.authUri()).isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
    }

    @Test
    @DisplayName("authenticate throws 400 when Google email is not verified")
    void authenticate_unverifiedGoogleEmail_throwsBadRequest() {
        GoogleAuthRequest request = new GoogleAuthRequest(null, "raw-id-token", "state-123", null, null);
        GoogleUserInfo userInfo = new GoogleUserInfo("sub-123", "user@example.com", "Test User", false);
        when(tokenVerifier.verifyIdToken("raw-id-token")).thenReturn(userInfo);

        assertThatThrownBy(() -> googleAuthService.authenticate(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("not verified");
                });
    }

    @Test
    @DisplayName("authenticate successfully logs in an already linked ACTIVE user")
    void authenticate_linkedActiveUser_logsIn() {
        GoogleAuthRequest request = new GoogleAuthRequest(null, "valid-token", "state-1", null, null);
        GoogleUserInfo userInfo = new GoogleUserInfo("google-sub-999", "alice@example.com", "Alice Smith", true);
        when(tokenVerifier.verifyIdToken("valid-token")).thenReturn(userInfo);

        User user = buildUser("alice@example.com", "Alice", "Smith");
        UserExternalIdentity identity = new UserExternalIdentity(user, "GOOGLE", "google-sub-999", "alice@example.com");

        when(externalIdentityRepository.findByProviderAndProviderSubject("GOOGLE", "google-sub-999"))
                .thenReturn(Optional.of(identity));
        when(tokenService.generateAccessToken(any(AuthenticatedUser.class))).thenReturn("access-token-jwt");

        LoginResponse response = googleAuthService.authenticate(request);

        assertThat(response.accessToken()).isEqualTo("access-token-jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.email()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("authenticate rejects linked user if account has been deactivated (INACTIVE)")
    void authenticate_linkedInactiveUser_throwsForbidden() {
        GoogleAuthRequest request = new GoogleAuthRequest(null, "valid-token", "state-1", null, null);
        GoogleUserInfo userInfo = new GoogleUserInfo("google-sub-999", "alice@example.com", "Alice Smith", true);
        when(tokenVerifier.verifyIdToken("valid-token")).thenReturn(userInfo);

        User user = buildUser("alice@example.com", "Alice", "Smith");
        user.deactivate();
        UserExternalIdentity identity = new UserExternalIdentity(user, "GOOGLE", "google-sub-999", "alice@example.com");

        when(externalIdentityRepository.findByProviderAndProviderSubject("GOOGLE", "google-sub-999"))
                .thenReturn(Optional.of(identity));

        assertThatThrownBy(() -> googleAuthService.authenticate(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("deactivated");
                });
    }

    @Test
    @DisplayName("authenticate rejects uninvited Google user (403 Forbidden)")
    void authenticate_uninvitedUser_throwsForbidden() {
        GoogleAuthRequest request = new GoogleAuthRequest(null, "valid-token", "state-1", null, null);
        GoogleUserInfo userInfo = new GoogleUserInfo("google-sub-stranger", "stranger@gmail.com", "Stranger", true);
        when(tokenVerifier.verifyIdToken("valid-token")).thenReturn(userInfo);

        when(externalIdentityRepository.findByProviderAndProviderSubject("GOOGLE", "google-sub-stranger"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("stranger@gmail.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> googleAuthService.authenticate(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("not registered with FlowDesk");
                });
    }

    @Test
    @DisplayName("authenticate links invited user when valid invitation token is provided")
    void authenticate_invitedUserWithToken_activatesAndLinks() {
        GoogleAuthRequest request = new GoogleAuthRequest(null, "valid-token", "state-1", "raw-invitation-token", null);
        GoogleUserInfo userInfo = new GoogleUserInfo("google-sub-invitee", "invitee@company.com", "New Hire", true);
        when(tokenVerifier.verifyIdToken("valid-token")).thenReturn(userInfo);

        User activatedUser = buildUser("invitee@company.com", "New", "Hire");

        when(externalIdentityRepository.findByProviderAndProviderSubject("GOOGLE", "google-sub-invitee"))
                .thenReturn(Optional.empty());
        when(userInvitationService.acceptInvitationWithGoogle("raw-invitation-token"))
                .thenReturn(activatedUser);
        when(tokenService.generateAccessToken(any(AuthenticatedUser.class))).thenReturn("access-token-jwt");

        LoginResponse response = googleAuthService.authenticate(request);

        assertThat(response.accessToken()).isEqualTo("access-token-jwt");

        ArgumentCaptor<UserExternalIdentity> identityCaptor = ArgumentCaptor.forClass(UserExternalIdentity.class);
        verify(externalIdentityRepository).save(identityCaptor.capture());
        UserExternalIdentity savedIdentity = identityCaptor.getValue();
        assertThat(savedIdentity.getUser()).isEqualTo(activatedUser);
        assertThat(savedIdentity.getProvider()).isEqualTo("GOOGLE");
        assertThat(savedIdentity.getProviderSubject()).isEqualTo("google-sub-invitee");
        assertThat(savedIdentity.getEmail()).isEqualTo("invitee@company.com");

        verify(auditService).recordSystemAction(
                eq(AuditAction.USER_OAUTH_LINKED),
                eq(AuditTargetType.USER),
                eq(activatedUser.getId()),
                eq("invitee@company.com"),
                any()
        );
    }

    @Test
    @DisplayName("authenticate rejects invited user attempting Google login without invitation token")
    void authenticate_invitedUserWithoutToken_throwsBadRequest() {
        GoogleAuthRequest request = new GoogleAuthRequest(null, "valid-token", "state-1", null, null);
        GoogleUserInfo userInfo = new GoogleUserInfo("google-sub-pending", "pending@company.com", "Pending User", true);
        when(tokenVerifier.verifyIdToken("valid-token")).thenReturn(userInfo);

        User invitedUser = User.createInvitedUser("pending@company.com", "Pending", "User", "unusable-hash");
        invitedUser.assignRole(new Role(RoleCode.EMPLOYEE, "Employee"));

        when(externalIdentityRepository.findByProviderAndProviderSubject("GOOGLE", "google-sub-pending"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("pending@company.com"))
                .thenReturn(Optional.of(invitedUser));

        assertThatThrownBy(() -> googleAuthService.authenticate(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("outstanding invitation");
                });
    }

    @Test
    @DisplayName("authenticate creates single-use pending linking transaction and throws OAuthLinkingRequiredException for unlinked active user")
    void authenticate_activeUser_createsPendingLinkingTransactionAndThrowsException() {
        GoogleAuthRequest request = new GoogleAuthRequest(null, "valid-token", "state-1", null, null);
        GoogleUserInfo userInfo = new GoogleUserInfo("google-sub-active", "active@company.com", "Active User", true);
        when(tokenVerifier.verifyIdToken("valid-token")).thenReturn(userInfo);

        User activeUser = buildUser("active@company.com", "Active", "User");

        when(externalIdentityRepository.findByProviderAndProviderSubject("GOOGLE", "google-sub-active"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("active@company.com"))
                .thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> googleAuthService.authenticate(request))
                .isInstanceOf(OAuthLinkingRequiredException.class)
                .satisfies(ex -> {
                    OAuthLinkingRequiredException linkingEx = (OAuthLinkingRequiredException) ex;
                    assertThat(linkingEx.getEmail()).isEqualTo("active@company.com");
                    assertThat(linkingEx.getProvider()).isEqualTo("GOOGLE");
                    assertThat(linkingEx.getLinkToken()).isNotBlank();
                });

        ArgumentCaptor<UserPendingOAuthLink> linkCaptor = ArgumentCaptor.forClass(UserPendingOAuthLink.class);
        verify(pendingLinkRepository).save(linkCaptor.capture());
        UserPendingOAuthLink savedLink = linkCaptor.getValue();
        assertThat(savedLink.getUser()).isEqualTo(activeUser);
        assertThat(savedLink.getProvider()).isEqualTo("GOOGLE");
        assertThat(savedLink.getProviderSubject()).isEqualTo("google-sub-active");
        assertThat(savedLink.getEmail()).isEqualTo("active@company.com");
        assertThat(savedLink.getFailedAttempts()).isZero();
        assertThat(savedLink.isConsumed()).isFalse();
    }

    @Test
    @DisplayName("completeLinking successfully links account when valid password is confirmed")
    void completeLinking_validPassword_consumesTransactionAndLinks() throws Exception {
        String rawToken = "raw-secret-linking-token";
        String tokenHash = hashToken(rawToken);

        User activeUser = buildUser("active@company.com", "Active", "User");
        Instant expiresAt = Instant.now().plusSeconds(600);
        UserPendingOAuthLink pendingLink = new UserPendingOAuthLink(
                activeUser,
                "GOOGLE",
                "google-sub-1234",
                "active@company.com",
                tokenHash,
                expiresAt
        );

        when(pendingLinkRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(pendingLink));
        when(userRepository.findByIdForUpdate(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("correctPassword123", activeUser.getPasswordHash())).thenReturn(true);
        when(externalIdentityRepository.existsByProviderAndProviderSubject("GOOGLE", "google-sub-1234")).thenReturn(false);
        when(tokenService.generateAccessToken(any(AuthenticatedUser.class))).thenReturn("new-access-token");

        LoginResponse response = googleAuthService.completeLinking(rawToken, "correctPassword123");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.email()).isEqualTo("active@company.com");
        assertThat(pendingLink.isConsumed()).isTrue();

        verify(pendingLinkRepository).save(pendingLink);
        verify(externalIdentityRepository).save(any(UserExternalIdentity.class));
        verify(auditService).recordUserAction(
                eq(activeUser),
                eq(AuditAction.USER_OAUTH_LINKED),
                eq(AuditTargetType.USER),
                eq(activeUser.getId()),
                eq("active@company.com"),
                any()
        );
    }

    @Test
    @DisplayName("completeLinking increments failed attempts and rejects wrong password")
    void completeLinking_wrongPassword_incrementsFailedAttempts() throws Exception {
        String rawToken = "raw-secret-linking-token";
        String tokenHash = hashToken(rawToken);

        User activeUser = buildUser("active@company.com", "Active", "User");
        Instant expiresAt = Instant.now().plusSeconds(600);
        UserPendingOAuthLink pendingLink = new UserPendingOAuthLink(
                activeUser,
                "GOOGLE",
                "google-sub-1234",
                "active@company.com",
                tokenHash,
                expiresAt
        );

        when(pendingLinkRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(pendingLink));
        when(userRepository.findByIdForUpdate(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrongPassword", activeUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> googleAuthService.completeLinking(rawToken, "wrongPassword"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("Invalid password for account linking");
                    assertThat(rse.getReason()).contains("4 attempts remaining");
                });

        assertThat(pendingLink.getFailedAttempts()).isEqualTo(1);
        assertThat(pendingLink.isConsumed()).isFalse();
        verify(pendingLinkRepository).save(pendingLink);
    }

    @Test
    @DisplayName("completeLinking permanently invalidates transaction when failed attempts reach limit (5)")
    void completeLinking_exceededAttempts_invalidatesTransaction() throws Exception {
        String rawToken = "raw-secret-linking-token";
        String tokenHash = hashToken(rawToken);

        User activeUser = buildUser("active@company.com", "Active", "User");
        Instant expiresAt = Instant.now().plusSeconds(600);
        UserPendingOAuthLink pendingLink = new UserPendingOAuthLink(
                activeUser,
                "GOOGLE",
                "google-sub-1234",
                "active@company.com",
                tokenHash,
                expiresAt
        );
        // Already failed 4 times
        for (int i = 0; i < 4; i++) {
            pendingLink.incrementFailedAttempts();
        }

        when(pendingLinkRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(pendingLink));
        when(userRepository.findByIdForUpdate(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrongPassword5", activeUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> googleAuthService.completeLinking(rawToken, "wrongPassword5"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("Too many failed password attempts. This linking transaction has been invalidated");
                });

        assertThat(pendingLink.getFailedAttempts()).isEqualTo(5);
        assertThat(pendingLink.isConsumed()).isTrue();
        verify(pendingLinkRepository).save(pendingLink);
    }

    @Test
    @DisplayName("completeLinking rejects already consumed linking transaction (replay protection)")
    void completeLinking_alreadyConsumed_throwsBadRequest() throws Exception {
        String rawToken = "raw-token";
        String tokenHash = hashToken(rawToken);

        User activeUser = buildUser("active@company.com", "Active", "User");
        Instant expiresAt = Instant.now().plusSeconds(600);
        UserPendingOAuthLink pendingLink = new UserPendingOAuthLink(
                activeUser,
                "GOOGLE",
                "google-sub-1234",
                "active@company.com",
                tokenHash,
                expiresAt
        );
        pendingLink.markConsumed(); // Already consumed

        when(pendingLinkRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(pendingLink));

        assertThatThrownBy(() -> googleAuthService.completeLinking(rawToken, "anyPassword"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("already been used");
                });
    }

    @Test
    @DisplayName("completeLinking rejects expired linking transaction")
    void completeLinking_expiredTransaction_throwsBadRequest() throws Exception {
        String rawToken = "raw-token";
        String tokenHash = hashToken(rawToken);

        User activeUser = buildUser("active@company.com", "Active", "User");
        // Expired 5 minutes ago
        Instant expiresAt = Instant.now().minusSeconds(300);
        UserPendingOAuthLink pendingLink = new UserPendingOAuthLink(
                activeUser,
                "GOOGLE",
                "google-sub-1234",
                "active@company.com",
                tokenHash,
                expiresAt
        );

        when(pendingLinkRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(pendingLink));

        assertThatThrownBy(() -> googleAuthService.completeLinking(rawToken, "anyPassword"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("expired");
                });
    }

    @Test
    @DisplayName("completeLinking rejects nonexistent token")
    void completeLinking_nonexistentToken_throwsBadRequest() throws Exception {
        String rawToken = "nonexistent-token";
        String tokenHash = hashToken(rawToken);

        when(pendingLinkRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> googleAuthService.completeLinking(rawToken, "anyPassword"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("Invalid or expired");
                });
    }

    @Test
    @DisplayName("completeLinking rejects if user has been deactivated since initiation")
    void completeLinking_userDeactivated_throwsForbidden() throws Exception {
        String rawToken = "raw-token";
        String tokenHash = hashToken(rawToken);

        User activeUser = buildUser("active@company.com", "Active", "User");
        activeUser.deactivate(); // Inactive now
        Instant expiresAt = Instant.now().plusSeconds(600);
        UserPendingOAuthLink pendingLink = new UserPendingOAuthLink(
                activeUser,
                "GOOGLE",
                "google-sub-1234",
                "active@company.com",
                tokenHash,
                expiresAt
        );

        when(pendingLinkRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(pendingLink));
        when(userRepository.findByIdForUpdate(activeUser.getId())).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> googleAuthService.completeLinking(rawToken, "anyPassword"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("deactivated");
                });
    }

    @Test
    @DisplayName("completeLinking prevents concurrent duplicate linking across multiple threads")
    void completeLinking_concurrentCalls_onlyOneSucceeds() throws Exception {
        String rawToken = "concurrent-linking-token";
        String tokenHash = hashToken(rawToken);

        User activeUser = buildUser("active@company.com", "Active", "User");
        Instant expiresAt = Instant.now().plusSeconds(600);
        UserPendingOAuthLink pendingLink = new UserPendingOAuthLink(
                activeUser,
                "GOOGLE",
                "google-sub-concurrent",
                "active@company.com",
                tokenHash,
                expiresAt
        );

        when(pendingLinkRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(pendingLink));
        when(userRepository.findByIdForUpdate(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("validPassword123!", activeUser.getPasswordHash())).thenReturn(true);
        when(tokenService.generateAccessToken(any(AuthenticatedUser.class))).thenReturn("access-token-concurrent");

        int threadCount = 2;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger failureCount = new java.util.concurrent.atomic.AtomicInteger();

        Runnable task = () -> {
            try {
                startLatch.await();
                // Synchronize on pendingLink object simulating pessimistic database row locking held across transaction
                synchronized (pendingLink) {
                    googleAuthService.completeLinking(rawToken, "validPassword123!");
                }
                successCount.incrementAndGet();
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                    failureCount.incrementAndGet();
                }
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        };

        executor.submit(task);
        executor.submit(task);
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);
        assertThat(pendingLink.isConsumed()).isTrue();
    }

    @Test
    @DisplayName("authenticate rejects authorization code when state parameter is missing")
    void authenticate_codeWithoutState_throwsBadRequest() {
        GoogleAuthRequest request = new GoogleAuthRequest("code-123", null, null, null, "verifier-abc");

        assertThatThrownBy(() -> googleAuthService.authenticate(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("OAuth state parameter is required");
                });
    }

    @Test
    @DisplayName("completeLinking throws OAuthLinkingInvalidPasswordException with correct attempt metadata")
    void completeLinking_throwsOAuthLinkingInvalidPasswordException_withMetadata() throws Exception {
        String rawToken = "raw-secret-token";
        String tokenHash = hashToken(rawToken);

        User activeUser = buildUser("active@company.com", "Active", "User");
        Instant expiresAt = Instant.now().plusSeconds(600);
        UserPendingOAuthLink pendingLink = new UserPendingOAuthLink(
                activeUser,
                "GOOGLE",
                "google-sub-1234",
                "active@company.com",
                tokenHash,
                expiresAt
        );

        when(pendingLinkRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(pendingLink));
        when(userRepository.findByIdForUpdate(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrongPassword", activeUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> googleAuthService.completeLinking(rawToken, "wrongPassword"))
                .isInstanceOf(OAuthLinkingInvalidPasswordException.class)
                .satisfies(ex -> {
                    OAuthLinkingInvalidPasswordException lipe = (OAuthLinkingInvalidPasswordException) ex;
                    assertThat(lipe.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(lipe.getRemainingAttempts()).isEqualTo(4);
                    assertThat(lipe.isPermanentlyInvalidated()).isFalse();
                });
    }
}
