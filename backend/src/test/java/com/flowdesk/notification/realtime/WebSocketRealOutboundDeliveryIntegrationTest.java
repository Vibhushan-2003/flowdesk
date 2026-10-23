
package com.flowdesk.notification.realtime;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeast;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class WebSocketRealOutboundDeliveryIntegrationTest {

    private static final String SUBSCRIPTION_DESTINATION =
            "/user/queue/notifications";

    private static final String BROKER_DESTINATION =
            "/queue/notifications";

    @LocalServerPort
    private int port;

    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtAuthenticationConverter authenticationConverter;

    @Autowired
    private WebSocketSessionRegistry sessionRegistry;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SimpUserRegistry simpUserRegistry;

    @Autowired
    @Qualifier("clientOutboundChannel")
    private AbstractMessageChannel outboundChannel;

    @Test
    void realPrivateNotificationMustUseRegisteredSession()
            throws Exception {

        String tokenValue = "integration-test-token";
        String userId = "test-user-37";

        String validNotification =
                "Day 37 notification probe";

        String blockedNotification =
                "This notification must be blocked";

        /*
         * STEP 1
         *
         * Create a test-only JWT.
         *
         * The JWT and authentication converter
         * are mocked for this integration test.
         */
        Jwt jwt = Jwt.withTokenValue(tokenValue)
                .header("alg", "HS256")
                .subject(userId)
                .build();

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(
                        jwt,
                        List.of(),
                        userId
                );

        when(jwtDecoder.decode(tokenValue))
                .thenReturn(jwt);

        when(authenticationConverter.convert(jwt))
                .thenReturn(authentication);

        /*
         * STEP 2
         *
         * Capture real outbound messages.
         *
         * This interceptor is added after the
         * configured outbound security interceptor.
         */
        BlockingQueue<Message<?>> outboundMessages =
                new LinkedBlockingQueue<>();

        BlockingQueue<String> receivedNotifications =
                new LinkedBlockingQueue<>();

        AtomicReference<String> stompFailure =
                new AtomicReference<>();

        ChannelInterceptor captureInterceptor =
                new ChannelInterceptor() {

                    @Override
                    public Message<?> preSend(
                            Message<?> message,
                            MessageChannel channel
                    ) {

                        SimpMessageType messageType =
                                SimpMessageHeaderAccessor
                                        .getMessageType(
                                                message.getHeaders()
                                        );

                        if (messageType != SimpMessageType.MESSAGE) {
                            return message;
                        }

                        String destination =
                                SimpMessageHeaderAccessor
                                        .getDestination(
                                                message.getHeaders()
                                        );

                        if (destination != null
                                && destination.startsWith(
                                        BROKER_DESTINATION
                                )) {

                            outboundMessages.offer(message);
                        }

                        return message;
                    }
                };

        /*
         * STEP 3
         *
         * Prepare the real WebSocket STOMP client.
         */
        WebSocketStompClient client =
                new WebSocketStompClient(
                        new StandardWebSocketClient()
                );

        client.setMessageConverter(
                new StringMessageConverter()
        );

        outboundChannel.addInterceptor(captureInterceptor);

        StompSession session = null;

        try {

            /*
             * STEP 4
             *
             * Connect to the actual /ws endpoint
             * using the JWT in STOMP CONNECT.
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

            StompSessionHandlerAdapter sessionHandler =
                    new StompSessionHandlerAdapter() {

                        @Override
                        public void handleFrame(
                                StompHeaders headers,
                                Object payload
                        ) {

                            stompFailure.set(
                                    "Server sent a STOMP ERROR frame"
                            );
                        }

                        @Override
                        public void handleException(
                                StompSession session,
                                StompCommand command,
                                StompHeaders headers,
                                byte[] payload,
                                Throwable exception
                        ) {

                            stompFailure.set(
                                    "STOMP processing error: "
                                            + exception
                                                    .getClass()
                                                    .getSimpleName()
                            );
                        }

                        @Override
                        public void handleTransportError(
                                StompSession session,
                                Throwable exception
                        ) {

                            stompFailure.set(
                                    "STOMP transport error: "
                                            + exception
                                                    .getClass()
                                                    .getSimpleName()
                            );
                        }
                    };

            session = client.connectAsync(
                    "ws://localhost:" + port + "/ws",
                    handshakeHeaders,
                    connectHeaders,
                    sessionHandler
            ).get(10, TimeUnit.SECONDS);

            assertTrue(
                    session.isConnected(),
                    "STOMP session should be connected"
            );

            /*
             * STEP 5
             *
             * Subscribe to private notifications.
             */
            session.subscribe(
                    SUBSCRIPTION_DESTINATION,
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

                            receivedNotifications.offer(
                                    (String) payload
                            );
                        }
                    }
            );

            /*
             * STEP 6
             *
             * Wait for the actual subscription
             * to appear in Spring's user registry.
             */
            waitForSubscription(
                    userId,
                    SUBSCRIPTION_DESTINATION,
                    stompFailure
            );

            /*
             * STEP 7
             *
             * Send a valid private notification.
             */
            messagingTemplate.convertAndSendToUser(
                    userId,
                    BROKER_DESTINATION,
                    validNotification
            );

            /*
             * STEP 8
             *
             * Confirm successful client delivery.
             */
            String received =
                    receivedNotifications.poll(
                            10,
                            TimeUnit.SECONDS
                    );

            assertEquals(
                    validNotification,
                    received,
                    "Client must receive its private notification"
            );

            /*
             * STEP 9
             *
             * Inspect the actual outbound message
             * generated by Spring's broker.
             */
            Message<?> outbound =
                    outboundMessages.poll(
                            10,
                            TimeUnit.SECONDS
                    );

            assertNotNull(
                    outbound,
                    "No real outbound MESSAGE was captured"
            );

            String sessionId =
                    SimpMessageHeaderAccessor.getSessionId(
                            outbound.getHeaders()
                    );

            assertNotNull(
                    sessionId,
                    "Outbound message must contain a session ID"
            );

            assertFalse(
                    sessionId.isBlank(),
                    "Outbound session ID must not be blank"
            );

            /*
             * STEP 10
             *
             * Confirm the outbound session
             * belongs to the authenticated user.
             */
            JwtAuthenticationToken registeredAuthentication =
                    sessionRegistry.find(sessionId)
                            .orElseThrow(
                                    () -> new AssertionError(
                                            "Outbound session has no "
                                                    + "registered JWT authentication"
                                    )
                            );

            assertEquals(
                    userId,
                    registeredAuthentication.getName(),
                    "Outbound session must belong to the expected user"
            );

            assertEquals(
                    tokenValue,
                    registeredAuthentication
                            .getToken()
                            .getTokenValue(),
                    "Registered session must contain the expected JWT"
            );

            assertTrue(
                    session.isConnected(),
                    "Client should remain connected after valid delivery"
            );

            /*
             * STEP 11
             *
             * Simulate JWT expiration.
             *
             * The connection has already been
             * authenticated and remains open.
             *
             * From now on, the decoder rejects
             * the existing JWT.
             */
            when(jwtDecoder.decode(tokenValue))
                    .thenThrow(
                            new JwtException("Token expired")
                    );

            /*
             * STEP 12
             *
             * Attempt another private notification.
             */
            messagingTemplate.convertAndSendToUser(
                    userId,
                    BROKER_DESTINATION,
                    blockedNotification
            );

            /*
             * STEP 13
             *
             * The client must not receive
             * the blocked notification.
             */
            String unexpectedDelivery =
                    receivedNotifications.poll(
                            2,
                            TimeUnit.SECONDS
                    );

            assertNull(
                    unexpectedDelivery,
                    "Expired JWT session must not receive "
                            + "private notifications"
            );

            /*
             * STEP 14
             *
             * The capture interceptor was registered
             * after the outbound security interceptor.
             *
             * If security blocks the message, the
             * capture interceptor must not see it.
             */
            Message<?> unexpectedOutbound =
                    outboundMessages.poll(
                            1,
                            TimeUnit.SECONDS
                    );

            assertNull(
                    unexpectedOutbound,
                    "Expired JWT notification must be "
                            + "blocked by outbound security"
            );

            /*
             * STEP 15
             *
             * Confirm the JWT decoder was used
             * by the authentication and delivery path.
             */
            verify(
                    jwtDecoder,
                    atLeast(2)
            ).decode(tokenValue);

        } finally {

            /*
             * STEP 16
             *
             * Clean up the WebSocket session
             * and the test-only interceptor.
             */
            if (session != null && session.isConnected()) {
                session.disconnect();
            }

            outboundChannel.removeInterceptor(
                    captureInterceptor
            );

            client.stop();
        }
    }

    /*
     * Helper:
     *
     * Wait for Spring's real subscription registry
     * rather than expecting a STOMP RECEIPT.
     */
    private void waitForSubscription(
            String userId,
            String destination,
            AtomicReference<String> stompFailure
    ) throws InterruptedException {

        long deadline =
                System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(10);

        while (System.nanoTime() < deadline) {

            String failure = stompFailure.get();

            if (failure != null) {
                fail(failure);
            }

            SimpUser user =
                    simpUserRegistry.getUser(userId);

            if (user != null) {

                boolean subscriptionExists =
                        user.getSessions()
                                .stream()
                                .flatMap(
                                        registeredSession ->
                                                registeredSession
                                                        .getSubscriptions()
                                                        .stream()
                                )
                                .anyMatch(
                                        subscription ->
                                                destination.equals(
                                                        subscription
                                                                .getDestination()
                                                )
                                );

                if (subscriptionExists) {
                    return;
                }
            }

            TimeUnit.MILLISECONDS.sleep(50);
        }

        fail(
                "Timed out waiting for a registered "
                        + "STOMP subscription for the test user"
        );
    }
}
