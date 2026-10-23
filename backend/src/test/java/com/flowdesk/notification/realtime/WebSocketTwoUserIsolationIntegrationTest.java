
package com.flowdesk.notification.realtime;

import java.lang.reflect.Type;
import java.util.List;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class WebSocketTwoUserIsolationIntegrationTest {

    private static final String DESTINATION =
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
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SimpUserRegistry simpUserRegistry;

    @Test
    void notificationForUserAMustNotReachUserB()
            throws Exception {

        String userA = "isolation-user-a";
        String userB = "isolation-user-b";

        String tokenA = "isolation-token-a";
        String tokenB = "isolation-token-b";

        Jwt jwtA = createJwt(tokenA, userA);
        Jwt jwtB = createJwt(tokenB, userB);

        JwtAuthenticationToken authA =
                new JwtAuthenticationToken(
                        jwtA,
                        List.of(),
                        userA
                );

        JwtAuthenticationToken authB =
                new JwtAuthenticationToken(
                        jwtB,
                        List.of(),
                        userB
                );

        when(jwtDecoder.decode(tokenA)).thenReturn(jwtA);
        when(jwtDecoder.decode(tokenB)).thenReturn(jwtB);

        when(authenticationConverter.convert(jwtA))
                .thenReturn(authA);

        when(authenticationConverter.convert(jwtB))
                .thenReturn(authB);

        BlockingQueue<String> receivedA =
                new LinkedBlockingQueue<>();

        BlockingQueue<String> receivedB =
                new LinkedBlockingQueue<>();

        WebSocketStompClient clientA = createClient();
        WebSocketStompClient clientB = createClient();

        StompSession sessionA = null;
        StompSession sessionB = null;

        try {

            sessionA = connect(clientA, tokenA);
            sessionB = connect(clientB, tokenB);

            assertTrue(sessionA.isConnected());
            assertTrue(sessionB.isConnected());

            subscribe(sessionA, receivedA);
            subscribe(sessionB, receivedB);

            waitForSubscription(userA);
            waitForSubscription(userB);

            String notificationA =
                    "Private notification for User A";

            messagingTemplate.convertAndSendToUser(
                    userA,
                    BROKER_DESTINATION,
                    notificationA
            );

            assertEquals(
                    notificationA,
                    receivedA.poll(10, TimeUnit.SECONDS),
                    "User A must receive their own notification"
            );

            assertNull(
                    receivedB.poll(2, TimeUnit.SECONDS),
                    "User B must not receive User A's notification"
            );

            String notificationB =
                    "Private notification for User B";

            messagingTemplate.convertAndSendToUser(
                    userB,
                    BROKER_DESTINATION,
                    notificationB
            );

            assertEquals(
                    notificationB,
                    receivedB.poll(10, TimeUnit.SECONDS),
                    "User B must receive their own notification"
            );

            assertNull(
                    receivedA.poll(2, TimeUnit.SECONDS),
                    "User A must not receive User B's notification"
            );

        } finally {

            if (sessionA != null && sessionA.isConnected()) {
                sessionA.disconnect();
            }

            if (sessionB != null && sessionB.isConnected()) {
                sessionB.disconnect();
            }

            clientA.stop();
            clientB.stop();
        }
    }

    private Jwt createJwt(
            String token,
            String userId
    ) {
        return Jwt.withTokenValue(token)
                .header("alg", "HS256")
                .subject(userId)
                .build();
    }

    private WebSocketStompClient createClient() {

        WebSocketStompClient client =
                new WebSocketStompClient(
                        new StandardWebSocketClient()
                );

        client.setMessageConverter(
                new StringMessageConverter()
        );

        return client;
    }

    private StompSession connect(
            WebSocketStompClient client,
            String token
    ) throws Exception {

        StompHeaders headers = new StompHeaders();

        headers.add(
                "Authorization",
                "Bearer " + token
        );

        WebSocketHttpHeaders handshake =
                new WebSocketHttpHeaders();

        handshake.setOrigin(allowedOrigin);

        return client.connectAsync(
                "ws://localhost:" + port + "/ws",
                handshake,
                headers,
                new StompSessionHandlerAdapter() {}
        ).get(10, TimeUnit.SECONDS);
    }

    private void subscribe(
            StompSession session,
            BlockingQueue<String> received
    ) {

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
    }

    private void waitForSubscription(
            String userId
    ) throws InterruptedException {

        long deadline =
                System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(10);

        while (System.nanoTime() < deadline) {

            SimpUser user =
                    simpUserRegistry.getUser(userId);

            if (user != null) {

                boolean found =
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

                if (found) {
                    return;
                }
            }

            TimeUnit.MILLISECONDS.sleep(50);
        }

        fail(
                "Subscription not registered for " + userId
        );
    }
}
