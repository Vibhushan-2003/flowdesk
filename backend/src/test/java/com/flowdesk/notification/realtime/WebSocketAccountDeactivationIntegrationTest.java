
package com.flowdesk.notification.realtime;

import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.repository.UserRepository;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class WebSocketAccountDeactivationIntegrationTest {

    private static final String DESTINATION =
            "/user/queue/notifications";

    private static final String BROKER_DESTINATION =
            "/queue/notifications";

    @LocalServerPort
    private int port;

    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    /*
     * Mock token decoding only.
     *
     * Keep the real JwtAuthenticationConverter
     * and JwtCurrentUserAuthoritiesConverter.
     */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    /*
     * Mock the database boundary.
     *
     * This allows us to simulate the account
     * becoming INACTIVE without modifying
     * any actual PostgreSQL records.
     */
    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SimpUserRegistry simpUserRegistry;

    @Test
    void inactiveAccountMustStopReceivingNotifications()
            throws Exception {

        UUID userId = UUID.randomUUID();

        String principalName = userId.toString();
        String tokenValue = "deactivation-test-token";

        Jwt jwt = Jwt.withTokenValue(tokenValue)
                .header("alg", "HS256")
                .subject(principalName)
                .claim("roles", Set.of("EMPLOYEE"))
                .claim("credential_version", 1L)
                .build();

        when(jwtDecoder.decode(tokenValue))
                .thenReturn(jwt);

        /*
         * Prepare one employee role and
         * one user whose status can change.
         */
        Role employeeRole = mock(Role.class);
        User user = mock(User.class);

        when(employeeRole.getCode())
                .thenReturn(RoleCode.EMPLOYEE);

        when(user.getRoles())
                .thenReturn(Set.of(employeeRole));

        when(user.getCredentialVersion())
                .thenReturn(1L);

        /*
         * At first, the account is ACTIVE.
         */
        when(user.getStatus())
                .thenReturn(UserStatus.ACTIVE);

        when(userRepository.findWithRolesById(userId))
                .thenReturn(Optional.of(user));

        BlockingQueue<String> received =
                new LinkedBlockingQueue<>();

        WebSocketStompClient client =
                new WebSocketStompClient(
                        new StandardWebSocketClient()
                );

        client.setMessageConverter(
                new StringMessageConverter()
        );

        StompSession session = null;

        try {

            /*
             * STEP 1
             * Connect using the active account.
             */
            StompHeaders connectHeaders =
                    new StompHeaders();

            connectHeaders.add(
                    "Authorization",
                    "Bearer " + tokenValue
            );

            WebSocketHttpHeaders handshakeHeaders =
                    new WebSocketHttpHeaders();

            handshakeHeaders.setOrigin(allowedOrigin);

            session = client.connectAsync(
                    "ws://localhost:" + port + "/ws",
                    handshakeHeaders,
                    connectHeaders,
                    new StompSessionHandlerAdapter() {}
            ).get(10, TimeUnit.SECONDS);

            assertTrue(
                    session.isConnected(),
                    "Active user must connect successfully"
            );

            /*
             * STEP 2
             * Subscribe to private notifications.
             */
            session.subscribe(
                    DESTINATION,
                    new StompFrameHandler() {

                        @Override
                        public Type getPayloadType(
                                StompHeaders headers
                        ) {
                            return String.class;
                        }

                        @Override
                        public void handleFrame(
                                StompHeaders headers,
                                Object payload
                        ) {
                            received.offer((String) payload);
                        }
                    }
            );

            waitForSubscription(principalName);

            /*
             * STEP 3
             * An ACTIVE user must receive
             * their private notification.
             */
            String allowedMessage =
                    "Notification for active account";

            messagingTemplate.convertAndSendToUser(
                    principalName,
                    BROKER_DESTINATION,
                    allowedMessage
            );

            assertEquals(
                    allowedMessage,
                    received.poll(10, TimeUnit.SECONDS),
                    "Active user must receive notification"
            );

            /*
             * STEP 4
             * Simulate account deactivation.
             *
             * This changes the repository's
             * response, not PostgreSQL itself.
             */
            when(user.getStatus())
                    .thenReturn(UserStatus.INACTIVE);

            /*
             * STEP 5
             * Try to deliver another notification
             * through the same WebSocket session.
             */
            String blockedMessage =
                    "Notification for inactive account";

            messagingTemplate.convertAndSendToUser(
                    principalName,
                    BROKER_DESTINATION,
                    blockedMessage
            );

            /*
             * STEP 6
             * The connected client must not
             * receive the second notification.
             */
            assertNull(
                    received.poll(2, TimeUnit.SECONDS),
                    "Inactive account must not receive notifications"
            );

            /*
             * STEP 7
             * Verify current user status was
             * consulted during authentication
             * and outbound revalidation.
             */
            verify(
                    userRepository,
                    atLeast(2)
            ).findWithRolesById(userId);

        } finally {

            if (session != null && session.isConnected()) {
                session.disconnect();
            }

            client.stop();
        }
    }

    private void waitForSubscription(
            String principalName
    ) throws InterruptedException {

        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(10);

        while (System.nanoTime() < deadline) {

            SimpUser user =
                    simpUserRegistry.getUser(principalName);

            if (user != null) {

                boolean subscribed =
                        user.getSessions()
                                .stream()
                                .flatMap(
                                        session ->
                                                session.getSubscriptions()
                                                        .stream()
                                )
                                .anyMatch(
                                        subscription ->
                                                DESTINATION.equals(
                                                        subscription.getDestination()
                                                )
                                );

                if (subscribed) {
                    return;
                }
            }

            TimeUnit.MILLISECONDS.sleep(50);
        }

        fail(
                "Timed out waiting for authenticated "
                        + "WebSocket subscription"
        );
    }
}
