package com.flowdesk.notification.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;

import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import org.springframework.security.core.Authentication;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import org.springframework.stereotype.Component;

@Component
public class WebSocketJwtChannelInterceptor
        implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER =
            "Authorization";

    private static final String BEARER_PREFIX =
            "Bearer ";

    private final JwtDecoder jwtDecoder;

    private final JwtAuthenticationConverter
            jwtAuthenticationConverter;

    public WebSocketJwtChannelInterceptor(
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) {
        this.jwtDecoder =
                jwtDecoder;

        this.jwtAuthenticationConverter =
                jwtAuthenticationConverter;
    }

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT
                != accessor.getCommand()) {

            return message;
        }

        String authorizationHeader =
                accessor.getFirstNativeHeader(
                        AUTHORIZATION_HEADER
                );

        if (authorizationHeader == null
                || authorizationHeader.isBlank()) {

            throw new MessagingException(
                    "Missing WebSocket authorization token"
            );
        }

        if (!authorizationHeader.startsWith(
                BEARER_PREFIX
        )) {

            throw new MessagingException(
                    "Invalid WebSocket authorization header"
            );
        }

        String token =
                authorizationHeader
                        .substring(
                                BEARER_PREFIX.length()
                        )
                        .trim();

        if (token.isBlank()) {
            throw new MessagingException(
                    "Missing WebSocket authorization token"
            );
        }

        try {
            Jwt jwt =
                    jwtDecoder.decode(
                            token
                    );

            Authentication authentication =
                    jwtAuthenticationConverter
                            .convert(jwt);

            if (authentication == null
                    || !authentication
                            .isAuthenticated()) {

                throw new MessagingException(
                        "WebSocket authentication failed"
                );
            }

            accessor.setUser(
                    authentication
            );

            return message;
        }
        catch (JwtException exception) {
            throw new MessagingException(
                    "Invalid or expired WebSocket token",
                    exception
            );
        }
    }
}