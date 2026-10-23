
package com.flowdesk.notification.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketJwtChannelInterceptorTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Mock
    private WebSocketSessionRegistry sessionRegistry;

    @Mock
    private MessageChannel messageChannel;

    private WebSocketJwtChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketJwtChannelInterceptor(
                jwtDecoder,
                jwtAuthenticationConverter,
                sessionRegistry
        );
    }

    /*
     * TEST 1
     *
     * A valid JWT authenticates CONNECT
     * and registers the session.
     */
    @Test
    void validBearerTokenShouldAuthenticateConnectFrame() {

        String token = "valid-jwt-token";
        String sessionId = "session-123";

        Jwt jwt = createJwt(token);

        JwtAuthenticationToken authentication =
                createAuthentication(jwt);

        when(jwtDecoder.decode(token))
                .thenReturn(jwt);

        when(jwtAuthenticationConverter.convert(jwt))
                .thenReturn(authentication);

        StompHeaderAccessor accessor =
                createAccessor(
                        StompCommand.CONNECT,
                        sessionId
                );

        accessor.setNativeHeader(
                "Authorization",
                "Bearer " + token
        );

        Message<byte[]> message = createMessage(accessor);

        Message<?> result =
                interceptor.preSend(message, messageChannel);

        assertSame(message, result);
        assertSame(authentication, accessor.getUser());

        verify(jwtDecoder).decode(token);
        verify(jwtAuthenticationConverter).convert(jwt);

        verify(sessionRegistry).register(
                sessionId,
                authentication
        );
    }

    /*
     * TEST 2
     *
     * Missing Authorization rejects CONNECT.
     */
    @Test
    void missingAuthorizationHeaderShouldRejectConnectFrame() {

        StompHeaderAccessor accessor =
                createAccessor(
                        StompCommand.CONNECT,
                        "session-missing-header"
                );

        Message<byte[]> message = createMessage(accessor);

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(message, messageChannel)
        );

        verify(jwtDecoder, never()).decode(anyString());

        verify(sessionRegistry, never()).register(
                anyString(),
                any(JwtAuthenticationToken.class)
        );
    }

    /*
     * TEST 3
     *
     * A non-Bearer header rejects CONNECT.
     */
    @Test
    void nonBearerAuthorizationHeaderShouldRejectConnectFrame() {

        StompHeaderAccessor accessor =
                createAccessor(
                        StompCommand.CONNECT,
                        "session-basic-header"
                );

        accessor.setNativeHeader(
                "Authorization",
                "Basic abc123"
        );

        Message<byte[]> message = createMessage(accessor);

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(message, messageChannel)
        );

        verify(jwtDecoder, never()).decode(anyString());

        verify(sessionRegistry, never()).register(
                anyString(),
                any(JwtAuthenticationToken.class)
        );
    }

    /*
     * TEST 4
     *
     * Invalid JWT rejects CONNECT.
     */
    @Test
    void invalidJwtShouldRejectConnectFrame() {

        String token = "invalid-jwt-token";

        when(jwtDecoder.decode(token))
                .thenThrow(new JwtException("Invalid token"));

        StompHeaderAccessor accessor =
                createAccessor(
                        StompCommand.CONNECT,
                        "session-invalid"
                );

        accessor.setNativeHeader(
                "Authorization",
                "Bearer " + token
        );

        Message<byte[]> message = createMessage(accessor);

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(message, messageChannel)
        );

        verify(jwtDecoder).decode(token);

        verify(jwtAuthenticationConverter, never())
                .convert(any(Jwt.class));

        verify(sessionRegistry, never()).register(
                anyString(),
                any(JwtAuthenticationToken.class)
        );
    }

    /*
     * TEST 5
     *
     * DISCONNECT can pass through without
     * requiring a new JWT.
     */
    @Test
    void nonAuthenticationFrameShouldPassThrough() {

        StompHeaderAccessor accessor =
                createAccessor(
                        StompCommand.DISCONNECT,
                        "session-disconnect"
                );

        Message<byte[]> message = createMessage(accessor);

        Message<?> result =
                interceptor.preSend(message, messageChannel);

        assertSame(message, result);

        verify(jwtDecoder, never()).decode(anyString());
    }

    /*
     * TEST 6
     *
     * A currently inactive account cannot CONNECT,
     * even when JWT decoding succeeds.
     */
    @Test
    void inactiveAccountCannotConnectWithValidJwt() {

        String token = "valid-but-inactive-account-token";

        Jwt jwt = createJwt(token);

        when(jwtDecoder.decode(token))
                .thenReturn(jwt);

        when(jwtAuthenticationConverter.convert(jwt))
                .thenThrow(
                        new OAuth2AuthenticationException(
                                new OAuth2Error("invalid_token")
                        )
                );

        StompHeaderAccessor accessor =
                createAccessor(
                        StompCommand.CONNECT,
                        "session-inactive"
                );

        accessor.setNativeHeader(
                "Authorization",
                "Bearer " + token
        );

        Message<byte[]> message = createMessage(accessor);

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(message, messageChannel)
        );

        assertNull(accessor.getUser());

        verify(jwtDecoder).decode(token);
        verify(jwtAuthenticationConverter).convert(jwt);

        verify(sessionRegistry, never()).register(
                anyString(),
                any(JwtAuthenticationToken.class)
        );
    }

    /*
     * TEST 7
     *
     * An unauthenticated client cannot SUBSCRIBE.
     */
    @Test
    void unauthenticatedClientCannotSubscribeToNotifications() {

        StompHeaderAccessor accessor =
                createAccessor(
                        StompCommand.SUBSCRIBE,
                        "session-unauthenticated"
                );

        accessor.setDestination(
                "/user/queue/notifications"
        );

        Message<byte[]> message = createMessage(accessor);

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(message, messageChannel)
        );

        verify(jwtDecoder, never()).decode(anyString());
    }

    /*
     * TEST 8
     *
     * A registered JWT session can SUBSCRIBE
     * after successful account revalidation.
     */
    @Test
    void authenticatedClientCanSubscribeToNotifications() {

        String token = "valid-session-token";
        String sessionId = "session-subscribed";

        Jwt jwt = createJwt(token);

        JwtAuthenticationToken sessionAuthentication =
                createAuthentication(jwt);

        JwtAuthenticationToken currentAuthentication =
                createAuthentication(jwt);

        when(sessionRegistry.find(sessionId))
                .thenReturn(Optional.of(sessionAuthentication));

        when(jwtDecoder.decode(token))
                .thenReturn(jwt);

        when(jwtAuthenticationConverter.convert(jwt))
                .thenReturn(currentAuthentication);

        StompHeaderAccessor accessor =
                createAccessor(
                        StompCommand.SUBSCRIBE,
                        sessionId
                );

        accessor.setDestination(
                "/user/queue/notifications"
        );

        accessor.setUser(sessionAuthentication);

        Message<byte[]> message = createMessage(accessor);

        Message<?> result =
                interceptor.preSend(message, messageChannel);

        assertSame(message, result);

        verify(sessionRegistry).find(sessionId);
        verify(jwtDecoder).decode(token);
        verify(jwtAuthenticationConverter).convert(jwt);
    }

    /*
     * TEST 9
     *
     * A cached non-JWT Principal
     * cannot SUBSCRIBE.
     */
    @Test
    void cachedAuthenticationWithoutJwtCannotSubscribe() {

        AbstractAuthenticationToken cachedAuthentication =
                mock(AbstractAuthenticationToken.class);

        StompHeaderAccessor accessor =
                createAccessor(
                        StompCommand.SUBSCRIBE,
                        "session-cached"
                );

        accessor.setDestination(
                "/user/queue/notifications"
        );

        accessor.setUser(cachedAuthentication);

        Message<byte[]> message = createMessage(accessor);

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(message, messageChannel)
        );

        verify(jwtDecoder, never()).decode(anyString());
    }

    /*
     * TEST 10
     *
     * An existing session cannot create
     * new subscriptions if its account
     * is no longer active.
     */
    @Test
    void inactiveExistingSessionCannotSubscribe() {

        String token = "existing-session-token";
        String sessionId = "session-now-inactive";

        Jwt jwt = createJwt(token);

        JwtAuthenticationToken sessionAuthentication =
                createAuthentication(jwt);

        when(sessionRegistry.find(sessionId))
                .thenReturn(Optional.of(sessionAuthentication));

        when(jwtDecoder.decode(token))
                .thenReturn(jwt);

        when(jwtAuthenticationConverter.convert(jwt))
                .thenThrow(
                        new OAuth2AuthenticationException(
                                new OAuth2Error("invalid_token")
                        )
                );

        StompHeaderAccessor accessor =
                createAccessor(
                        StompCommand.SUBSCRIBE,
                        sessionId
                );

        accessor.setDestination(
                "/user/queue/notifications"
        );

        accessor.setUser(sessionAuthentication);

        Message<byte[]> message = createMessage(accessor);

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(message, messageChannel)
        );

        verify(sessionRegistry).find(sessionId);
        verify(jwtDecoder).decode(token);
        verify(jwtAuthenticationConverter).convert(jwt);
    }

    /*
     * TEST 11
     *
     * Unauthorized destinations are rejected
     * before JWT or registry validation.
     *
     * Six parameter values = six test executions.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/queue/notifications",
            "/queue/anything",
            "/user/queue/admin",
            "/user/queue/notifications/other",
            "/topic/notifications",
            "/app/notifications"
    })
    void authenticatedUserCannotSubscribeToUnauthorizedDestination(
            String destination
    ) {

        Jwt jwt = createJwt("destination-test-token");

        JwtAuthenticationToken authentication =
                createAuthentication(jwt);

        StompHeaderAccessor accessor =
                createAccessor(
                        StompCommand.SUBSCRIBE,
                        "session-destination-check"
                );

        accessor.setDestination(destination);
        accessor.setUser(authentication);

        Message<byte[]> message = createMessage(accessor);

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(message, messageChannel)
        );

        verify(jwtDecoder, never()).decode(anyString());
    }

    /*
     * TEST 12
     *
     * Clients cannot directly SEND messages.
     *
     * Three parameter values = three executions.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/queue/notifications",
            "/app/notifications",
            "/topic/notifications"
    })
    void clientCannotSendMessagesToProtectedDestinations(
            String destination
    ) {

        StompHeaderAccessor accessor =
                createAccessor(
                        StompCommand.SEND,
                        "session-send-denied"
                );

        accessor.setDestination(destination);

        Message<byte[]> message = createMessage(accessor);

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(message, messageChannel)
        );
    }

    /*
     * TEST 13 - NEW
     *
     * A registered session must have
     * the same user and JWT as the Principal.
     */
    @Test
    void mismatchedSessionJwtMustRejectSubscription() {

        String sessionId = "session-mismatch";

        JwtAuthenticationToken registeredAuthentication =
                createAuthentication(
                        createJwt("original-token")
                );

        JwtAuthenticationToken forgedAuthentication =
                createAuthentication(
                        createJwt("different-token")
                );

        when(sessionRegistry.find(sessionId))
                .thenReturn(Optional.of(registeredAuthentication));

        StompHeaderAccessor accessor =
                createAccessor(
                        StompCommand.SUBSCRIBE,
                        sessionId
                );

        accessor.setDestination(
                "/user/queue/notifications"
        );

        accessor.setUser(forgedAuthentication);

        Message<byte[]> message = createMessage(accessor);

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(message, messageChannel)
        );

        verify(jwtDecoder, never()).decode(anyString());
    }

    /*
     * TEST 14 - NEW
     *
     * A JWT Principal without a registry entry
     * cannot SUBSCRIBE.
     */
    @Test
    void unregisteredSessionCannotSubscribe() {

        String sessionId = "session-not-registered";

        JwtAuthenticationToken authentication =
                createAuthentication(
                        createJwt("valid-looking-token")
                );

        when(sessionRegistry.find(sessionId))
                .thenReturn(Optional.empty());

        StompHeaderAccessor accessor =
                createAccessor(
                        StompCommand.SUBSCRIBE,
                        sessionId
                );

        accessor.setDestination(
                "/user/queue/notifications"
        );

        accessor.setUser(authentication);

        Message<byte[]> message = createMessage(accessor);

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(message, messageChannel)
        );

        verify(jwtDecoder, never()).decode(anyString());
    }

    /*
     * TEST 15 - NEW
     *
     * CONNECT must not register a session
     * if the session ID is missing.
     */
    @Test
    void missingSessionIdMustRejectConnect() {

        String token = "valid-token-no-session";

        Jwt jwt = createJwt(token);

        JwtAuthenticationToken authentication =
                createAuthentication(jwt);

        when(jwtDecoder.decode(token))
                .thenReturn(jwt);

        when(jwtAuthenticationConverter.convert(jwt))
                .thenReturn(authentication);

        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(
                        StompCommand.CONNECT
                );

        accessor.setNativeHeader(
                "Authorization",
                "Bearer " + token
        );

        Message<byte[]> message = createMessage(accessor);

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(message, messageChannel)
        );

        verify(sessionRegistry, never()).register(
                anyString(),
                any(JwtAuthenticationToken.class)
        );
    }

    /*
     * TEST 16 - NEW
     *
     * Invalid JWT must never create a
     * registered WebSocket session.
     */
    @Test
    void invalidJwtMustNotRegisterSession() {

        String token = "rejected-token";

        when(jwtDecoder.decode(token))
                .thenThrow(
                        new JwtException("JWT expired")
                );

        StompHeaderAccessor accessor =
                createAccessor(
                        StompCommand.CONNECT,
                        "session-rejected"
                );

        accessor.setNativeHeader(
                "Authorization",
                "Bearer " + token
        );

        Message<byte[]> message = createMessage(accessor);

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(message, messageChannel)
        );

        verify(sessionRegistry, never()).register(
                anyString(),
                any(JwtAuthenticationToken.class)
        );
    }

    /*
     * Helpers
     */

    private Jwt createJwt(String tokenValue) {

        return Jwt.withTokenValue(tokenValue)
                .header("alg", "HS256")
                .subject("user-123")
                .build();
    }

    private JwtAuthenticationToken createAuthentication(
            Jwt jwt
    ) {

        return new JwtAuthenticationToken(
                jwt,
                List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_EMPLOYEE"
                        )
                )
        );
    }

    private StompHeaderAccessor createAccessor(
            StompCommand command,
            String sessionId
    ) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(command);

        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);

        return accessor;
    }

    private Message<byte[]> createMessage(
            StompHeaderAccessor accessor
    ) {

        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }
}
