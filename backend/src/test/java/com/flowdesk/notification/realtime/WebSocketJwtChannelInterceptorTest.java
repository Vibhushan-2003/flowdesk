package com.flowdesk.notification.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;

import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import org.springframework.messaging.support.MessageBuilder;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    private JwtAuthenticationConverter
            jwtAuthenticationConverter;

    @Mock
    private MessageChannel messageChannel;

    private WebSocketJwtChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {

        interceptor =
                new WebSocketJwtChannelInterceptor(
                        jwtDecoder,
                        jwtAuthenticationConverter
                );
    }

    @Test
    void validBearerTokenShouldAuthenticateConnectFrame() {

        String token =
                "valid-jwt-token";

        Jwt jwt =
                mock(Jwt.class);

        AbstractAuthenticationToken authentication =
                mock(AbstractAuthenticationToken.class);

        when(jwtDecoder.decode(token))
                .thenReturn(jwt);

        when(
                jwtAuthenticationConverter
                        .convert(jwt)
        ).thenReturn(
                authentication
        );

        when(authentication.isAuthenticated())
                .thenReturn(true);

        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(
                        StompCommand.CONNECT
                );

        accessor.setNativeHeader(
                "Authorization",
                "Bearer " + token
        );

        accessor.setLeaveMutable(true);

        Message<byte[]> message =
                MessageBuilder.createMessage(
                        new byte[0],
                        accessor.getMessageHeaders()
                );

        Message<?> result =
                interceptor.preSend(
                        message,
                        messageChannel
                );

        assertSame(
                message,
                result
        );

        assertSame(
                authentication,
                accessor.getUser()
        );

        verify(jwtDecoder)
                .decode(token);

        verify(jwtAuthenticationConverter)
                .convert(jwt);
    }

    @Test
    void missingAuthorizationHeaderShouldRejectConnectFrame() {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(
                        StompCommand.CONNECT
                );

        accessor.setLeaveMutable(true);

        Message<byte[]> message =
                MessageBuilder.createMessage(
                        new byte[0],
                        accessor.getMessageHeaders()
                );

        assertThrows(
                MessagingException.class,
                () ->
                        interceptor.preSend(
                                message,
                                messageChannel
                        )
        );

        verify(
                jwtDecoder,
                never()
        ).decode(
                anyString()
        );
    }

    @Test
    void nonBearerAuthorizationHeaderShouldRejectConnectFrame() {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(
                        StompCommand.CONNECT
                );

        accessor.setNativeHeader(
                "Authorization",
                "Basic abc123"
        );

        accessor.setLeaveMutable(true);

        Message<byte[]> message =
                MessageBuilder.createMessage(
                        new byte[0],
                        accessor.getMessageHeaders()
                );

        assertThrows(
                MessagingException.class,
                () ->
                        interceptor.preSend(
                                message,
                                messageChannel
                        )
        );

        verify(
                jwtDecoder,
                never()
        ).decode(
                anyString()
        );
    }

    @Test
    void invalidJwtShouldRejectConnectFrame() {

        String token =
                "invalid-jwt-token";

        when(jwtDecoder.decode(token))
                .thenThrow(
                        new JwtException(
                                "Invalid token"
                        )
                );

        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(
                        StompCommand.CONNECT
                );

        accessor.setNativeHeader(
                "Authorization",
                "Bearer " + token
        );

        accessor.setLeaveMutable(true);

        Message<byte[]> message =
                MessageBuilder.createMessage(
                        new byte[0],
                        accessor.getMessageHeaders()
                );

        assertThrows(
                MessagingException.class,
                () ->
                        interceptor.preSend(
                                message,
                                messageChannel
                        )
        );

        verify(jwtDecoder)
                .decode(token);

        verify(
                jwtAuthenticationConverter,
                never()
        ).convert(
                any(Jwt.class)
        );
    }

    @Test
    void nonConnectFrameShouldPassThroughWithoutAuthentication() {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(
                        StompCommand.SUBSCRIBE
                );

        accessor.setLeaveMutable(true);

        Message<byte[]> message =
                MessageBuilder.createMessage(
                        new byte[0],
                        accessor.getMessageHeaders()
                );

        Message<?> result =
                interceptor.preSend(
                        message,
                        messageChannel
                );

        assertSame(
                message,
                result
        );

        verify(
                jwtDecoder,
                never()
        ).decode(
                anyString()
        );
    }
}