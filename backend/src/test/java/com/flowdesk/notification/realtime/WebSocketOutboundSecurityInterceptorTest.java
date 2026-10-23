
package com.flowdesk.notification.realtime;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketOutboundSecurityInterceptorTest {

    private static final String SESSION_ID =
            "session-123";

    private static final String USER_ID =
            "user-123";

    private static final String TOKEN_VALUE =
            "test-jwt-token";

    private static final String NOTIFICATION_DESTINATION =
            "/queue/notifications-user123";

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private JwtAuthenticationConverter authenticationConverter;

    @Mock
    private WebSocketSessionRegistry sessionRegistry;

    @Mock
    private MessageChannel messageChannel;

    private WebSocketOutboundSecurityInterceptor interceptor;

    @BeforeEach
    void setUp() {

        interceptor =
                new WebSocketOutboundSecurityInterceptor(
                        jwtDecoder,
                        authenticationConverter,
                        sessionRegistry
                );
    }

    /*
     * TEST 1
     *
     * A valid registered JWT session
     * must receive its private notification.
     */
    @Test
    void validRegisteredSessionMustReceiveNotification() {

        Jwt jwt = createJwt(TOKEN_VALUE);

        JwtAuthenticationToken authentication =
                createAuthentication(jwt);

        when(sessionRegistry.find(SESSION_ID))
                .thenReturn(Optional.of(authentication));

        when(jwtDecoder.decode(TOKEN_VALUE))
                .thenReturn(jwt);

        when(authenticationConverter.convert(jwt))
                .thenReturn(authentication);

        Message<byte[]> message =
                createMessage(
                        SimpMessageType.MESSAGE,
                        SESSION_ID,
                        NOTIFICATION_DESTINATION
                );

        Message<?> result =
                interceptor.preSend(message, messageChannel);

        assertSame(message, result);

        verify(jwtDecoder).decode(TOKEN_VALUE);
    }

    /*
     * TEST 2
     *
     * An unregistered session must not
     * receive a private notification.
     */
    @Test
    void unregisteredSessionMustNotReceiveNotification() {

        when(sessionRegistry.find(SESSION_ID))
                .thenReturn(Optional.empty());

        Message<byte[]> message =
                createMessage(
                        SimpMessageType.MESSAGE,
                        SESSION_ID,
                        NOTIFICATION_DESTINATION
                );

        Message<?> result =
                interceptor.preSend(message, messageChannel);

        assertNull(result);

        verifyNoInteractions(
                jwtDecoder,
                authenticationConverter
        );
    }

    /*
     * TEST 3
     *
     * An expired JWT must not receive
     * private notifications.
     */
    @Test
    void expiredJwtMustNotReceiveNotification() {

        Jwt jwt = createJwt(TOKEN_VALUE);

        JwtAuthenticationToken authentication =
                createAuthentication(jwt);

        when(sessionRegistry.find(SESSION_ID))
                .thenReturn(Optional.of(authentication));

        when(jwtDecoder.decode(TOKEN_VALUE))
                .thenThrow(
                        new JwtException("Token expired")
                );

        Message<byte[]> message =
                createMessage(
                        SimpMessageType.MESSAGE,
                        SESSION_ID,
                        NOTIFICATION_DESTINATION
                );

        Message<?> result =
                interceptor.preSend(message, messageChannel);

        assertNull(result);

        verify(authenticationConverter, never())
                .convert(jwt);
    }

    /*
     * TEST 4
     *
     * A disabled account must not receive
     * private notifications.
     *
     * Model converter rejection as null.
     */
    @Test
    void disabledAccountMustNotReceiveNotification() {

        Jwt jwt = createJwt(TOKEN_VALUE);

        JwtAuthenticationToken authentication =
                createAuthentication(jwt);

        when(sessionRegistry.find(SESSION_ID))
                .thenReturn(Optional.of(authentication));

        when(jwtDecoder.decode(TOKEN_VALUE))
                .thenReturn(jwt);

        when(authenticationConverter.convert(jwt))
                .thenReturn(null);

        Message<byte[]> message =
                createMessage(
                        SimpMessageType.MESSAGE,
                        SESSION_ID,
                        NOTIFICATION_DESTINATION
                );

        Message<?> result =
                interceptor.preSend(message, messageChannel);

        assertNull(result);
    }

    /*
     * TEST 5
     *
     * A changed user identity must not
     * receive another user's notification.
     */
    @Test
    void changedIdentityMustNotReceiveNotification() {

        Jwt jwt = createJwt(TOKEN_VALUE);

        JwtAuthenticationToken registeredAuthentication =
                createAuthentication(jwt);

        JwtAuthenticationToken changedAuthentication =
                new JwtAuthenticationToken(
                        jwt,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_EMPLOYEE"
                                )
                        ),
                        "different-user"
                );

        when(sessionRegistry.find(SESSION_ID))
                .thenReturn(
                        Optional.of(registeredAuthentication)
                );

        when(jwtDecoder.decode(TOKEN_VALUE))
                .thenReturn(jwt);

        when(authenticationConverter.convert(jwt))
                .thenReturn(changedAuthentication);

        Message<byte[]> message =
                createMessage(
                        SimpMessageType.MESSAGE,
                        SESSION_ID,
                        NOTIFICATION_DESTINATION
                );

        Message<?> result =
                interceptor.preSend(message, messageChannel);

        assertNull(result);
    }

    /*
     * TEST 6
     *
     * A notification without a session ID
     * must be blocked.
     */
    @Test
    void missingSessionIdMustNotReceiveNotification() {

        Message<byte[]> message =
                createMessage(
                        SimpMessageType.MESSAGE,
                        null,
                        NOTIFICATION_DESTINATION
                );

        Message<?> result =
                interceptor.preSend(message, messageChannel);

        assertNull(result);

        verifyNoInteractions(
                sessionRegistry,
                jwtDecoder,
                authenticationConverter
        );
    }

    /*
     * TEST 7
     *
     * A CONNECT_ACK control frame
     * must pass without JWT validation.
     */
    @Test
    void connectedControlFrameMustPassWithoutJwtValidation() {

        Message<byte[]> message =
                createMessage(
                        SimpMessageType.CONNECT_ACK,
                        SESSION_ID,
                        null
                );

        Message<?> result =
                interceptor.preSend(message, messageChannel);

        assertSame(message, result);

        verifyNoInteractions(
                sessionRegistry,
                jwtDecoder,
                authenticationConverter
        );
    }

    /*
     * TEST 8
     *
     * Unrelated destinations must not
     * be affected by notification security.
     */
    @Test
    void otherDestinationsMustNotBeAffected() {

        Message<byte[]> message =
                createMessage(
                        SimpMessageType.MESSAGE,
                        SESSION_ID,
                        "/queue/other-feature"
                );

        Message<?> result =
                interceptor.preSend(message, messageChannel);

        assertSame(message, result);

        verifyNoInteractions(
                sessionRegistry,
                jwtDecoder,
                authenticationConverter
        );
    }

    /*
     * TEST 9
     *
     * An inactive account must be blocked
     * when the authentication converter
     * throws OAuth2AuthenticationException.
     */
    @Test
    void inactiveAccountRejectionMustBlockNotification() {

        Jwt jwt = createJwt(TOKEN_VALUE);

        JwtAuthenticationToken authentication =
                createAuthentication(jwt);

        when(sessionRegistry.find(SESSION_ID))
                .thenReturn(Optional.of(authentication));

        when(jwtDecoder.decode(TOKEN_VALUE))
                .thenReturn(jwt);

        when(authenticationConverter.convert(jwt))
                .thenThrow(
                        new OAuth2AuthenticationException(
                                new OAuth2Error(
                                        "invalid_token",
                                        "Account is unavailable",
                                        null
                                )
                        )
                );

        Message<byte[]> message =
                createMessage(
                        SimpMessageType.MESSAGE,
                        SESSION_ID,
                        NOTIFICATION_DESTINATION
                );

        Message<?> result =
                interceptor.preSend(message, messageChannel);

        assertNull(
                result,
                "Inactive accounts must not receive notifications"
        );

        verify(authenticationConverter).convert(jwt);
    }

    /*
     * DAY 38 - TEST 10
     *
     * A different JWT returned after
     * revalidation must block delivery,
     * even when the user ID is unchanged.
     */
    @Test
    void changedJwtTokenMustBlockNotification() {

        Jwt originalJwt = createJwt(TOKEN_VALUE);

        JwtAuthenticationToken registered =
                createAuthentication(originalJwt);

        Jwt changedJwt =
                createJwt("different-jwt-token");

        JwtAuthenticationToken changedAuthentication =
                createAuthentication(changedJwt);

        when(sessionRegistry.find(SESSION_ID))
                .thenReturn(Optional.of(registered));

        when(jwtDecoder.decode(TOKEN_VALUE))
                .thenReturn(originalJwt);

        when(authenticationConverter.convert(originalJwt))
                .thenReturn(changedAuthentication);

        Message<byte[]> message =
                createMessage(
                        SimpMessageType.MESSAGE,
                        SESSION_ID,
                        NOTIFICATION_DESTINATION
                );

        Message<?> result =
                interceptor.preSend(message, messageChannel);

        assertNull(result);

        verify(jwtDecoder).decode(TOKEN_VALUE);
    }

    /*
     * DAY 38 - TEST 11
     *
     * A registered JWT authentication
     * marked unauthenticated must not
     * receive notifications.
     */
    @Test
    void unauthenticatedRegisteredSessionMustBlockNotification() {

        Jwt jwt = createJwt(TOKEN_VALUE);

        JwtAuthenticationToken authentication =
                createAuthentication(jwt);

        authentication.setAuthenticated(false);

        when(sessionRegistry.find(SESSION_ID))
                .thenReturn(Optional.of(authentication));

        Message<byte[]> message =
                createMessage(
                        SimpMessageType.MESSAGE,
                        SESSION_ID,
                        NOTIFICATION_DESTINATION
                );

        Message<?> result =
                interceptor.preSend(message, messageChannel);

        assertNull(result);
    }

    /*
     * DAY 38 - TEST 12
     *
     * If the decoder returns null,
     * the notification must be blocked.
     */
    @Test
    void nullDecodedJwtMustBlockNotification() {

        Jwt jwt = createJwt(TOKEN_VALUE);

        JwtAuthenticationToken registered =
                createAuthentication(jwt);

        when(sessionRegistry.find(SESSION_ID))
                .thenReturn(Optional.of(registered));

        when(jwtDecoder.decode(TOKEN_VALUE))
                .thenReturn(null);

        Message<byte[]> message =
                createMessage(
                        SimpMessageType.MESSAGE,
                        SESSION_ID,
                        NOTIFICATION_DESTINATION
                );

        Message<?> result =
                interceptor.preSend(message, messageChannel);

        assertNull(result);

        verify(authenticationConverter, never())
                .convert(jwt);
    }

    /*
     * DAY 38 - TEST 13
     *
     * Unexpected decoder failure must
     * not allow private notification delivery.
     */
    @Test
    void unexpectedDecoderFailureMustBlockNotification() {

        Jwt jwt = createJwt(TOKEN_VALUE);

        JwtAuthenticationToken registered =
                createAuthentication(jwt);

        when(sessionRegistry.find(SESSION_ID))
                .thenReturn(Optional.of(registered));

        when(jwtDecoder.decode(TOKEN_VALUE))
                .thenThrow(
                        new IllegalStateException(
                                "Decoder unavailable"
                        )
                );

        Message<byte[]> message =
                createMessage(
                        SimpMessageType.MESSAGE,
                        SESSION_ID,
                        NOTIFICATION_DESTINATION
                );

        Message<?> result =
                interceptor.preSend(message, messageChannel);

        assertNull(result);

        verify(authenticationConverter, never())
                .convert(jwt);
    }

    /*
     * HELPER 1
     *
     * Create a JWT for testing.
     */
    private Jwt createJwt(String tokenValue) {

        return Jwt.withTokenValue(tokenValue)
                .header("alg", "HS256")
                .subject(USER_ID)
                .build();
    }

    /*
     * HELPER 2
     *
     * Create an authenticated JWT principal.
     */
    private JwtAuthenticationToken createAuthentication(
            Jwt jwt
    ) {

        return new JwtAuthenticationToken(
                jwt,
                List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_EMPLOYEE"
                        )
                ),
                USER_ID
        );
    }

    /*
     * HELPER 3
     *
     * Create an outbound STOMP message
     * with optional session and destination.
     */
    private Message<byte[]> createMessage(
            SimpMessageType messageType,
            String sessionId,
            String destination
    ) {

        SimpMessageHeaderAccessor accessor =
                SimpMessageHeaderAccessor.create(
                        messageType
                );

        if (sessionId != null) {
            accessor.setSessionId(sessionId);
        }

        if (destination != null) {
            accessor.setDestination(destination);
        }

        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }
}
