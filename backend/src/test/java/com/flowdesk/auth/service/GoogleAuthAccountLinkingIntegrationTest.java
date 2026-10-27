package com.flowdesk.auth.service;

import com.flowdesk.audit.service.AuditService;
import com.flowdesk.auth.domain.UserExternalIdentity;
import com.flowdesk.auth.domain.UserPendingOAuthLink;
import com.flowdesk.auth.dto.GoogleAuthRequest;
import com.flowdesk.auth.dto.GoogleUserInfo;
import com.flowdesk.auth.dto.LoginResponse;
import com.flowdesk.auth.exception.OAuthLinkingInvalidPasswordException;
import com.flowdesk.auth.exception.OAuthLinkingRequiredException;
import com.flowdesk.auth.repository.UserExternalIdentityRepository;
import com.flowdesk.auth.repository.UserPendingOAuthLinkRepository;
import com.flowdesk.auth.security.GoogleTokenVerifier;
import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.RoleRepository;
import com.flowdesk.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class GoogleAuthAccountLinkingIntegrationTest {

    @Autowired
    private GoogleAuthService googleAuthService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserPendingOAuthLinkRepository pendingLinkRepository;

    @Autowired
    private UserExternalIdentityRepository externalIdentityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private GoogleTokenVerifier tokenVerifier;

    @MockitoBean
    private AuditService auditService;

    private static final String TEST_PASSWORD = "ValidFlowDeskPassword123!";
    private Role employeeRole;

    @BeforeEach
    void setUp() {
        cleanUpTestData();
        employeeRole = roleRepository.findByCode(RoleCode.EMPLOYEE)
                .orElseGet(() -> roleRepository.save(new Role(RoleCode.EMPLOYEE, "Employee")));
    }

    @AfterEach
    void tearDown() {
        cleanUpTestData();
    }

    private void cleanUpTestData() {
        jdbcTemplate.update("DELETE FROM user_external_identities WHERE email LIKE '%@oauth-integration.test'");
        jdbcTemplate.update("DELETE FROM user_pending_oauth_links WHERE email LIKE '%@oauth-integration.test'");
    }

    private User createActiveUser(String email) {
        User user = new User(email, passwordEncoder.encode(TEST_PASSWORD), "Integration", "Tester");
        user.assignRole(employeeRole);
        return userRepository.save(user);
    }

    private String hashToken(String rawToken) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    @Test
    @DisplayName("authenticate durably commits UserPendingOAuthLink in PostgreSQL when OAuthLinkingRequiredException is thrown")
    void authenticate_activeUser_commitsPendingLinkToPostgreSQL() throws Exception {
        String email = "active.linker." + UUID.randomUUID() + "@oauth-integration.test";
        User user = createActiveUser(email);
        String googleSub = "gsub-" + UUID.randomUUID();

        when(tokenVerifier.exchangeCodeAndVerify(anyString(), any(), any()))
                .thenReturn(new GoogleUserInfo(googleSub, email, "Active Linker", true));

        GoogleAuthRequest request = new GoogleAuthRequest("code-abc", null, "state-xyz", null, "verifier-123");

        // Invoke through the Spring proxy
        String capturedToken;
        try {
            googleAuthService.authenticate(request);
            throw new AssertionError("Expected OAuthLinkingRequiredException to be thrown");
        } catch (OAuthLinkingRequiredException ex) {
            assertThat(ex.getEmail()).isEqualTo(email);
            assertThat(ex.getProvider()).isEqualTo("GOOGLE");
            assertThat(ex.getLinkToken()).isNotBlank();
            capturedToken = ex.getLinkToken();
        }

        // Verify that the record was committed to PostgreSQL and is visible to new transactions
        String tokenHash = hashToken(capturedToken);
        Optional<UserPendingOAuthLink> pendingOpt = pendingLinkRepository.findByTokenHash(tokenHash);

        assertThat(pendingOpt).isPresent();
        UserPendingOAuthLink pending = pendingOpt.get();
        assertThat(pending.getUser().getId()).isEqualTo(user.getId());
        assertThat(pending.getEmail()).isEqualTo(email);
        assertThat(pending.getProvider()).isEqualTo("GOOGLE");
        assertThat(pending.getProviderSubject()).isEqualTo(googleSub);
        assertThat(pending.getFailedAttempts()).isZero();
        assertThat(pending.isConsumed()).isFalse();
        assertThat(pending.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("completeLinking with valid password succeeds, consumes transaction, and creates external identity")
    void completeLinking_validPassword_succeedsAndConsumesTransaction() throws Exception {
        String email = "complete.linker." + UUID.randomUUID() + "@oauth-integration.test";
        User user = createActiveUser(email);
        String googleSub = "gsub-" + UUID.randomUUID();

        when(tokenVerifier.exchangeCodeAndVerify(anyString(), any(), any()))
                .thenReturn(new GoogleUserInfo(googleSub, email, "Complete Linker", true));

        GoogleAuthRequest request = new GoogleAuthRequest("code-complete", null, "state-complete", null, "verifier-c");
        String rawToken;
        try {
            googleAuthService.authenticate(request);
            throw new AssertionError("Expected OAuthLinkingRequiredException");
        } catch (OAuthLinkingRequiredException ex) {
            rawToken = ex.getLinkToken();
        }

        // Confirm linking with valid password
        LoginResponse response = googleAuthService.completeLinking(rawToken, TEST_PASSWORD);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.email()).isEqualTo(email);

        // Verify transaction is marked consumed in PostgreSQL
        String tokenHash = hashToken(rawToken);
        UserPendingOAuthLink pending = pendingLinkRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(pending.isConsumed()).isTrue();
        assertThat(pending.getConsumedAt()).isNotNull();

        // Verify UserExternalIdentity is created in PostgreSQL
        Optional<UserExternalIdentity> identityOpt = externalIdentityRepository
                .findByProviderAndProviderSubject("GOOGLE", googleSub);
        assertThat(identityOpt).isPresent();
        assertThat(identityOpt.get().getUser().getId()).isEqualTo(user.getId());
        assertThat(identityOpt.get().getEmail()).isEqualTo(email);
    }

    @Test
    @DisplayName("completeLinking rejects already consumed linking transaction (replay prevention)")
    void completeLinking_replayAttempt_isRejected() throws Exception {
        String email = "replay.linker." + UUID.randomUUID() + "@oauth-integration.test";
        createActiveUser(email);
        String googleSub = "gsub-" + UUID.randomUUID();

        when(tokenVerifier.exchangeCodeAndVerify(anyString(), any(), any()))
                .thenReturn(new GoogleUserInfo(googleSub, email, "Replay Linker", true));

        GoogleAuthRequest request = new GoogleAuthRequest("code-replay", null, "state-replay", null, "verifier-r");
        String rawToken;
        try {
            googleAuthService.authenticate(request);
            throw new AssertionError("Expected OAuthLinkingRequiredException");
        } catch (OAuthLinkingRequiredException ex) {
            rawToken = ex.getLinkToken();
        }

        // First attempt succeeds
        googleAuthService.completeLinking(rawToken, TEST_PASSWORD);

        // Second attempt must be rejected (replay attack)
        assertThatThrownBy(() -> googleAuthService.completeLinking(rawToken, TEST_PASSWORD))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("already been used");
                });
    }

    @Test
    @DisplayName("completeLinking wrong password increments and commits failedAttempts in PostgreSQL across errors")
    void completeLinking_wrongPassword_incrementsAndCommitsAttempts() throws Exception {
        String email = "attempts.linker." + UUID.randomUUID() + "@oauth-integration.test";
        createActiveUser(email);
        String googleSub = "gsub-" + UUID.randomUUID();

        when(tokenVerifier.exchangeCodeAndVerify(anyString(), any(), any()))
                .thenReturn(new GoogleUserInfo(googleSub, email, "Attempts Linker", true));

        GoogleAuthRequest request = new GoogleAuthRequest("code-att", null, "state-att", null, "verifier-a");
        String rawToken;
        try {
            googleAuthService.authenticate(request);
            throw new AssertionError("Expected OAuthLinkingRequiredException");
        } catch (OAuthLinkingRequiredException ex) {
            rawToken = ex.getLinkToken();
        }

        String tokenHash = hashToken(rawToken);

        // Attempt 1: Wrong password
        assertThatThrownBy(() -> googleAuthService.completeLinking(rawToken, "WrongPassword1!"))
                .isInstanceOf(OAuthLinkingInvalidPasswordException.class)
                .satisfies(ex -> {
                    OAuthLinkingInvalidPasswordException lipe = (OAuthLinkingInvalidPasswordException) ex;
                    assertThat(lipe.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(lipe.getRemainingAttempts()).isEqualTo(4);
                    assertThat(lipe.isPermanentlyInvalidated()).isFalse();
                });

        // Verify committed attempt in PostgreSQL
        UserPendingOAuthLink pendingAfter1 = pendingLinkRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(pendingAfter1.getFailedAttempts()).isEqualTo(1);
        assertThat(pendingAfter1.isConsumed()).isFalse();

        // Attempts 2, 3, 4: Wrong passwords
        for (int i = 2; i <= 4; i++) {
            final int expectedRemaining = 5 - i;
            assertThatThrownBy(() -> googleAuthService.completeLinking(rawToken, "WrongPassword" + expectedRemaining))
                    .isInstanceOf(OAuthLinkingInvalidPasswordException.class)
                    .satisfies(ex -> {
                        OAuthLinkingInvalidPasswordException lipe = (OAuthLinkingInvalidPasswordException) ex;
                        assertThat(lipe.getRemainingAttempts()).isEqualTo(expectedRemaining);
                    });

            UserPendingOAuthLink check = pendingLinkRepository.findByTokenHash(tokenHash).orElseThrow();
            assertThat(check.getFailedAttempts()).isEqualTo(i);
        }

        // Attempt 5: Exceeds limit -> permanently invalidates
        assertThatThrownBy(() -> googleAuthService.completeLinking(rawToken, "WrongPassword5"))
                .isInstanceOf(OAuthLinkingInvalidPasswordException.class)
                .satisfies(ex -> {
                    OAuthLinkingInvalidPasswordException lipe = (OAuthLinkingInvalidPasswordException) ex;
                    assertThat(lipe.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(lipe.isPermanentlyInvalidated()).isTrue();
                    assertThat(lipe.getReason()).contains("Too many failed password attempts");
                });

        // Verify permanently consumed in PostgreSQL
        UserPendingOAuthLink pendingAfter5 = pendingLinkRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(pendingAfter5.getFailedAttempts()).isEqualTo(5);
        assertThat(pendingAfter5.isConsumed()).isTrue();

        // Attempt 6: Rejected immediately because transaction is invalidated/consumed
        assertThatThrownBy(() -> googleAuthService.completeLinking(rawToken, TEST_PASSWORD))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("completeLinking rejects expired linking transaction")
    void completeLinking_expiredTransaction_isRejected() throws Exception {
        String email = "expired.linker." + UUID.randomUUID() + "@oauth-integration.test";
        User user = createActiveUser(email);
        String rawToken = "expired-raw-token-" + UUID.randomUUID();
        String tokenHash = hashToken(rawToken);

        // Insert pending link where created_at is 15 minutes ago and expires_at is 5 minutes ago (satisfying chk_pending_oauth_links_expiry)
        UUID linkId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO user_pending_oauth_links (
                    id, user_id, provider, provider_subject, email, token_hash, failed_attempts, created_at, expires_at
                ) VALUES (
                    ?, ?, 'GOOGLE', 'expired-sub', ?, ?, 0, CURRENT_TIMESTAMP - INTERVAL '15 minutes', CURRENT_TIMESTAMP - INTERVAL '5 minutes'
                )
                """,
                linkId, user.getId(), email, tokenHash
        );

        assertThatThrownBy(() -> googleAuthService.completeLinking(rawToken, TEST_PASSWORD))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("has expired");
                });
    }

    @Test
    @DisplayName("authenticate rejects inactive user account (403 Forbidden)")
    void authenticate_inactiveUser_throwsForbidden() {
        String email = "inactive." + UUID.randomUUID() + "@oauth-integration.test";
        User user = new User(email, passwordEncoder.encode(TEST_PASSWORD), "Inactive", "User");
        user.assignRole(employeeRole);
        user.deactivate();
        userRepository.save(user);

        when(tokenVerifier.exchangeCodeAndVerify(anyString(), any(), any()))
                .thenReturn(new GoogleUserInfo("sub-inactive", email, "Inactive User", true));

        GoogleAuthRequest request = new GoogleAuthRequest("code-inact", null, "state-inact", null, "verifier-i");

        assertThatThrownBy(() -> googleAuthService.authenticate(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("deactivated");
                });
    }

    @Test
    @DisplayName("authenticate rejects uninvited user (403 Forbidden)")
    void authenticate_uninvitedUser_throwsForbidden() {
        String email = "uninvited." + UUID.randomUUID() + "@oauth-integration.test";

        when(tokenVerifier.exchangeCodeAndVerify(anyString(), any(), any()))
                .thenReturn(new GoogleUserInfo("sub-uninvited", email, "Stranger", true));

        GoogleAuthRequest request = new GoogleAuthRequest("code-uninv", null, "state-uninv", null, "verifier-u");

        assertThatThrownBy(() -> googleAuthService.authenticate(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("not registered with FlowDesk");
                });
    }
}
