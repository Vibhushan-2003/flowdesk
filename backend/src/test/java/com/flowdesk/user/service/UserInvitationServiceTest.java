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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
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
class UserInvitationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserInvitationRepository userInvitationRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private EmailSender emailSender;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private AuditService auditService;

    private PasswordEncoder passwordEncoder;
    private UserInvitationProperties properties;
    private UserInvitationService invitationService;

    private User actorAdmin;
    private UUID actorAdminId;
    private Role employeeRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        properties = new UserInvitationProperties();
        properties.setFrontendOrigin("http://localhost:5173");
        properties.setTokenValidityHours(48);
        properties.setTokenEntropyBytes(32);

        invitationService = new UserInvitationService(
                userRepository,
                userInvitationRepository,
                roleRepository,
                passwordEncoder,
                emailSender,
                emailTemplateService,
                properties,
                auditService
        );

        adminRole = new Role(RoleCode.ADMIN, "Platform Administrator");
        employeeRole = new Role(RoleCode.EMPLOYEE, "Standard Employee");

        actorAdminId = UUID.randomUUID();
        actorAdmin = new User("admin@flowdesk.test", "hash", "Admin", "User");
        ReflectionTestUtils.setField(actorAdmin, "id", actorAdminId);
        actorAdmin.assignRole(adminRole);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("Admin Invitation Issuance Tests")
    class IssuanceTests {

        @Test
        @DisplayName("Should successfully issue invitation, save INVITED user, hash token, and record audit")
        void shouldIssueInvitationSuccessfully() {
            CreateUserInvitationRequest request = new CreateUserInvitationRequest(
                    "newhire@flowdesk.test",
                    "Jane",
                    "Doe",
                    Set.of(RoleCode.EMPLOYEE)
            );

            when(userRepository.existsByEmailIgnoreCase("newhire@flowdesk.test")).thenReturn(false);
            when(userRepository.findWithRolesById(actorAdminId)).thenReturn(Optional.of(actorAdmin));
            when(roleRepository.findByCodeIn(Set.of(RoleCode.EMPLOYEE))).thenReturn(List.of(employeeRole));
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
                return u;
            });
            when(emailTemplateService.buildUserInvitationEmail(any(), any(), any(), any()))
                    .thenReturn(EmailMessage.builder().to("newhire@flowdesk.test").subject("Invite").textBody("Body").build());
            when(emailSender.send(any())).thenReturn(EmailDeliveryResult.accepted("msg-1", "newhire@flowdesk.test"));

            UserResponse response = invitationService.createInvitation(request, actorAdminId);

            assertNotNull(response);
            assertEquals("newhire@flowdesk.test", response.email());
            assertEquals("Jane", response.firstName());
            assertEquals("Doe", response.lastName());
            assertEquals(UserStatus.INVITED, response.status());
            assertTrue(response.roles().contains(RoleCode.EMPLOYEE));

            // Verify User persistence has unusable password hash and INVITED status
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).saveAndFlush(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertEquals(UserStatus.INVITED, savedUser.getStatus());
            assertTrue(savedUser.getPasswordHash().startsWith("!INVITED!"));

            // Verify UserInvitation persistence
            ArgumentCaptor<UserInvitation> inviteCaptor = ArgumentCaptor.forClass(UserInvitation.class);
            verify(userInvitationRepository).save(inviteCaptor.capture());
            UserInvitation savedInvite = inviteCaptor.getValue();
            assertNotNull(savedInvite.getTokenHash());
            assertEquals(64, savedInvite.getTokenHash().length());
            assertNull(savedInvite.getConsumedAt());

            // Verify Audit
            verify(auditService).recordUserAction(
                    eq(actorAdmin),
                    eq(AuditAction.USER_INVITED),
                    eq(AuditTargetType.USER),
                    eq(savedUser.getId()),
                    eq("newhire@flowdesk.test"),
                    any()
            );

            // Verify outbound email link contains accept-invitation and frontend origin
            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailTemplateService).buildUserInvitationEmail(
                    eq("newhire@flowdesk.test"),
                    eq("Admin User"),
                    urlCaptor.capture(),
                    eq(Duration.ofHours(48))
            );
            assertTrue(urlCaptor.getValue().startsWith("http://localhost:5173/accept-invitation?token="));
        }

        @Test
        @DisplayName("Should reject invitation if email already exists")
        void shouldRejectDuplicateEmail() {
            CreateUserInvitationRequest request = new CreateUserInvitationRequest(
                    "existing@flowdesk.test",
                    "Jane",
                    "Doe",
                    Set.of(RoleCode.EMPLOYEE)
            );

            when(userRepository.existsByEmailIgnoreCase("existing@flowdesk.test")).thenReturn(true);

            assertThrows(DuplicateEmailException.class, () ->
                    invitationService.createInvitation(request, actorAdminId)
            );

            verify(userRepository, never()).saveAndFlush(any());
            verify(userInvitationRepository, never()).save(any());
            verify(auditService, never()).recordUserAction(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should reject invitation if roles are invalid")
        void shouldRejectInvalidRoles() {
            CreateUserInvitationRequest request = new CreateUserInvitationRequest(
                    "new@flowdesk.test",
                    "Jane",
                    "Doe",
                    Set.of(RoleCode.EMPLOYEE, RoleCode.ADMIN)
            );

            when(userRepository.existsByEmailIgnoreCase("new@flowdesk.test")).thenReturn(false);
            when(userRepository.findWithRolesById(actorAdminId)).thenReturn(Optional.of(actorAdmin));
            // Only 1 role resolved
            when(roleRepository.findByCodeIn(any())).thenReturn(List.of(employeeRole));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    invitationService.createInvitation(request, actorAdminId)
            );
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("Should handle email delivery failure as recoverable without rolling back invitation")
        void shouldTreatDeliveryFailureAsRecoverable() {
            CreateUserInvitationRequest request = new CreateUserInvitationRequest(
                    "recoverable@flowdesk.test",
                    "Jane",
                    "Doe",
                    Set.of(RoleCode.EMPLOYEE)
            );

            when(userRepository.existsByEmailIgnoreCase("recoverable@flowdesk.test")).thenReturn(false);
            when(userRepository.findWithRolesById(actorAdminId)).thenReturn(Optional.of(actorAdmin));
            when(roleRepository.findByCodeIn(any())).thenReturn(List.of(employeeRole));
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(i -> {
                User u = i.getArgument(0);
                ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
                return u;
            });
            when(emailTemplateService.buildUserInvitationEmail(any(), any(), any(), any()))
                    .thenReturn(EmailMessage.builder().to("recoverable@flowdesk.test").subject("Invite").textBody("Body").build());
            when(emailSender.send(any())).thenReturn(EmailDeliveryResult.failed("msg-err", "recoverable@flowdesk.test", "SMTP unavailable"));

            // Must succeed without throwing
            UserResponse response = invitationService.createInvitation(request, actorAdminId);
            assertNotNull(response);
            assertEquals(UserStatus.INVITED, response.status());
        }
        @Test
        @DisplayName("Should normalize trailing slash in frontend origin to prevent double slashes in invitation URL")
        void shouldNormalizeTrailingSlashInInvitationUrl() {
            properties.setFrontendOrigin("http://localhost:5173///");

            CreateUserInvitationRequest request = new CreateUserInvitationRequest(
                    "slash@flowdesk.test",
                    "Jane",
                    "Doe",
                    Set.of(RoleCode.EMPLOYEE)
            );

            when(userRepository.existsByEmailIgnoreCase("slash@flowdesk.test")).thenReturn(false);
            when(userRepository.findWithRolesById(actorAdminId)).thenReturn(Optional.of(actorAdmin));
            when(roleRepository.findByCodeIn(Set.of(RoleCode.EMPLOYEE))).thenReturn(List.of(employeeRole));
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
                return u;
            });
            when(emailTemplateService.buildUserInvitationEmail(any(), any(), any(), any()))
                    .thenReturn(EmailMessage.builder().to("slash@flowdesk.test").subject("Invite").textBody("Body").build());
            when(emailSender.send(any())).thenReturn(EmailDeliveryResult.accepted("msg-1", "slash@flowdesk.test"));

            invitationService.createInvitation(request, actorAdminId);

            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailTemplateService).buildUserInvitationEmail(
                    eq("slash@flowdesk.test"),
                    eq("Admin User"),
                    urlCaptor.capture(),
                    eq(Duration.ofHours(48))
            );
            assertTrue(urlCaptor.getValue().startsWith("http://localhost:5173/accept-invitation?token="));
            assertFalse(urlCaptor.getValue().contains("5173//"));
        }
    }

    @Nested
    @DisplayName("Invitation Resend Tests")
    class ResendTests {

        @Test
        @DisplayName("Should successfully resend invitation, revoke previous unconsumed token, and audit")
        void shouldResendInvitationSuccessfully() {
            UUID targetUserId = UUID.randomUUID();
            User invitedUser = User.createInvitedUser("invited@flowdesk.test", "Bob", "Smith", "!INVITED!xyz");
            ReflectionTestUtils.setField(invitedUser, "id", targetUserId);

            when(userRepository.findByIdForUpdate(targetUserId)).thenReturn(Optional.of(invitedUser));
            when(userRepository.findWithRolesById(actorAdminId)).thenReturn(Optional.of(actorAdmin));
            when(emailTemplateService.buildUserInvitationEmail(any(), any(), any(), any()))
                    .thenReturn(EmailMessage.builder().to("invited@flowdesk.test").subject("Invite").textBody("Body").build());
            when(emailSender.send(any())).thenReturn(EmailDeliveryResult.accepted("msg-2", "invited@flowdesk.test"));

            ResendInvitationResponse response = invitationService.resendInvitation(targetUserId, actorAdminId);

            assertNotNull(response);
            assertEquals("Invitation resent successfully", response.message());

            // Verify previous tokens invalidated
            verify(userInvitationRepository).invalidateActiveInvitationsForUser(eq(targetUserId), any(Instant.class));

            // Verify new token persisted
            verify(userInvitationRepository).save(any(UserInvitation.class));

            // Verify audit
            verify(auditService).recordUserAction(
                    eq(actorAdmin),
                    eq(AuditAction.USER_INVITATION_RESENT),
                    eq(AuditTargetType.USER),
                    eq(targetUserId),
                    eq("invited@flowdesk.test"),
                    any()
            );
        }

        @Test
        @DisplayName("Should reject resending invitation for ACTIVE user")
        void shouldRejectResendForActiveUser() {
            UUID targetUserId = UUID.randomUUID();
            User activeUser = new User("active@flowdesk.test", "hash", "Bob", "Smith");
            ReflectionTestUtils.setField(activeUser, "id", targetUserId);

            when(userRepository.findByIdForUpdate(targetUserId)).thenReturn(Optional.of(activeUser));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    invitationService.resendInvitation(targetUserId, actorAdminId)
            );
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason().contains("INVITED"));

            verify(userInvitationRepository, never()).invalidateActiveInvitationsForUser(any(), any());
            verify(userInvitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject resending invitation for INACTIVE user")
        void shouldRejectResendForInactiveUser() {
            UUID targetUserId = UUID.randomUUID();
            User inactiveUser = new User("inactive@flowdesk.test", "hash", "Bob", "Smith");
            inactiveUser.deactivate();
            ReflectionTestUtils.setField(inactiveUser, "id", targetUserId);

            when(userRepository.findByIdForUpdate(targetUserId)).thenReturn(Optional.of(inactiveUser));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    invitationService.resendInvitation(targetUserId, actorAdminId)
            );
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }
    }

    @Nested
    @DisplayName("Invitation Acceptance Tests")
    class AcceptanceTests {

        @Test
        @DisplayName("Should successfully accept invitation, set BCrypt password, bump credentialVersion, and activate user")
        void shouldAcceptInvitationSuccessfully() {
            String rawToken = "valid-raw-invitation-token-1234567890";
            String tokenHash = hashToken(rawToken);

            UUID userId = UUID.randomUUID();
            User user = User.createInvitedUser("invited@flowdesk.test", "Jane", "Doe", "!INVITED!abc");
            ReflectionTestUtils.setField(user, "id", userId);
            assertEquals(1L, user.getCredentialVersion());
            assertEquals(UserStatus.INVITED, user.getStatus());

            UserInvitation invitation = new UserInvitation(user, tokenHash, Instant.now().plus(Duration.ofHours(24)));

            when(userInvitationRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(invitation));
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
            when(userInvitationRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(invitation));

            AcceptInvitationRequest request = new AcceptInvitationRequest(
                    rawToken,
                    "SecurePassword123!",
                    "SecurePassword123!"
            );

            AcceptInvitationResponse response = invitationService.acceptInvitation(request);

            assertNotNull(response);
            assertEquals("Invitation accepted successfully. You can now log in.", response.message());

            // Verify user is ACTIVE, credential version bumped, and password hash updated with BCrypt
            assertEquals(UserStatus.ACTIVE, user.getStatus());
            assertEquals(2L, user.getCredentialVersion());
            assertTrue(passwordEncoder.matches("SecurePassword123!", user.getPasswordHash()));

            // Verify invitation marked consumed
            assertTrue(invitation.isConsumed());
            assertNotNull(invitation.getConsumedAt());

            // Verify strict lock ordering: user row locked FIRST, invitation row locked SECOND
            InOrder inOrder = inOrder(userRepository, userInvitationRepository);
            inOrder.verify(userRepository).findByIdForUpdate(userId);
            inOrder.verify(userInvitationRepository).findByTokenHashForUpdate(tokenHash);

            // Verify audit
            verify(auditService).recordSystemAction(
                    eq(AuditAction.USER_INVITATION_ACCEPTED),
                    eq(AuditTargetType.USER),
                    eq(userId),
                    eq("invited@flowdesk.test"),
                    any()
            );
        }

        @Test
        @DisplayName("Should reject acceptance when token is invalid or non-existent")
        void shouldRejectInvalidToken() {
            when(userInvitationRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            AcceptInvitationRequest request = new AcceptInvitationRequest("bogus-token", "ValidPassword123!", "ValidPassword123!");

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    invitationService.acceptInvitation(request)
            );
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("Should reject acceptance when token has expired")
        void shouldRejectExpiredToken() {
            String rawToken = "expired-token";
            String tokenHash = hashToken(rawToken);

            UUID userId = UUID.randomUUID();
            User user = User.createInvitedUser("invited@flowdesk.test", "Jane", "Doe", "!INVITED!abc");
            ReflectionTestUtils.setField(user, "id", userId);

            // Expired in past
            UserInvitation invitation = new UserInvitation(user, tokenHash, Instant.now().minus(Duration.ofHours(1)));

            when(userInvitationRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(invitation));
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
            when(userInvitationRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(invitation));

            AcceptInvitationRequest request = new AcceptInvitationRequest(rawToken, "ValidPassword123!", "ValidPassword123!");

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    invitationService.acceptInvitation(request)
            );
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason().contains("expired"));
        }

        @Test
        @DisplayName("Should reject acceptance when token was already consumed")
        void shouldRejectAlreadyConsumedToken() {
            String rawToken = "consumed-token";
            String tokenHash = hashToken(rawToken);

            UUID userId = UUID.randomUUID();
            User user = User.createInvitedUser("invited@flowdesk.test", "Jane", "Doe", "!INVITED!abc");
            ReflectionTestUtils.setField(user, "id", userId);

            UserInvitation invitation = new UserInvitation(user, tokenHash, Instant.now().plus(Duration.ofHours(24)));
            invitation.consume(Instant.now().minus(Duration.ofMinutes(5)));

            when(userInvitationRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(invitation));
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
            when(userInvitationRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(invitation));

            AcceptInvitationRequest request = new AcceptInvitationRequest(rawToken, "ValidPassword123!", "ValidPassword123!");

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    invitationService.acceptInvitation(request)
            );
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason().contains("already been used"));
        }

        @Test
        @DisplayName("Should reject passwords that do not match")
        void shouldRejectPasswordMismatch() {
            AcceptInvitationRequest request = new AcceptInvitationRequest(
                    "token",
                    "Password123!",
                    "Different123!"
            );

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    invitationService.acceptInvitation(request)
            );
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason().contains("match"));
        }

        @Test
        @DisplayName("Should reject passwords shorter than 8 characters")
        void shouldRejectShortPassword() {
            AcceptInvitationRequest request = new AcceptInvitationRequest(
                    "token",
                    "Short1!",
                    "Short1!"
            );

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    invitationService.acceptInvitation(request)
            );
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("Should prevent concurrent duplicate acceptance of same token across multiple threads")
        void shouldPreventConcurrentDuplicateAcceptance() throws Exception {
            String rawToken = "concurrent-token";
            String tokenHash = hashToken(rawToken);

            UUID userId = UUID.randomUUID();
            User user = User.createInvitedUser("invited@flowdesk.test", "Jane", "Doe", "!INVITED!abc");
            ReflectionTestUtils.setField(user, "id", userId);

            UserInvitation invitation = new UserInvitation(user, tokenHash, Instant.now().plus(Duration.ofHours(24)));

            when(userInvitationRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(invitation));
            when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
            when(userInvitationRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(invitation));

            AcceptInvitationRequest request = new AcceptInvitationRequest(rawToken, "NewSecurePassword1!", "NewSecurePassword1!");

            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger failureCount = new AtomicInteger();

            Runnable task = () -> {
                try {
                    startLatch.await();
                    // Synchronize on user object simulating pessimistic database row locking held across transaction
                    synchronized (user) {
                        invitationService.acceptInvitation(request);
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

            for (int i = 0; i < threadCount; i++) {
                executor.submit(task);
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(finished, "Both threads must complete within timeout");
            assertEquals(1, successCount.get(), "Exactly one acceptance request must succeed");
            assertEquals(1, failureCount.get(), "The duplicate acceptance request must be rejected with 400 Bad Request");
            assertTrue(invitation.isConsumed(), "Token must be marked consumed");
            assertEquals(UserStatus.ACTIVE, user.getStatus(), "User account must be activated");
            assertEquals(2L, user.getCredentialVersion(), "Credential version must be incremented once");
        }
    }

    @Nested
    @DisplayName("Cleanup Tests")
    class CleanupTests {
        @Test
        void shouldCallRepositoryDelete() {
            when(userInvitationRepository.deleteOldInvitations(any(Instant.class))).thenReturn(5);

            int deleted = invitationService.cleanupOldInvitations(Duration.ofDays(30));
            assertEquals(5, deleted);
            verify(userInvitationRepository).deleteOldInvitations(any(Instant.class));
        }
    }
}
