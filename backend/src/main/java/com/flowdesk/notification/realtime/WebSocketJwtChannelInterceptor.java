
package com.flowdesk.notification.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import org.springframework.stereotype.Component;

@Component
public class WebSocketJwtChannelInterceptor
        implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER =
            "Authorization";

    private static final String BEARER_PREFIX =
            "Bearer ";

    private static final String ALLOWED_SUBSCRIPTION_DESTINATION =
            "/user/queue/notifications";

    private final JwtDecoder jwtDecoder;

    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    private final WebSocketSessionRegistry sessionRegistry;

    public WebSocketJwtChannelInterceptor(
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            WebSocketSessionRegistry sessionRegistry
    ) {
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.sessionRegistry = sessionRegistry;
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

        /*
         * Non-STOMP messages, including internal
         * messaging and heartbeat handling,
         * are not processed as client commands.
         */
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (command == null) {
            return message;
        }

        /*
         * CONNECT:
         *
         * A new WebSocket/STOMP session must
         * authenticate using a valid JWT.
         */
        if (command == StompCommand.CONNECT) {
            return authenticateConnect(message, accessor);
        }

        /*
         * SEND:
         *
         * FlowDesk notifications are produced
         * by the backend. Clients cannot publish
         * arbitrary STOMP messages.
         */
        if (command == StompCommand.SEND) {
            throw new MessagingException(
                    "Client WebSocket SEND is not permitted"
            );
        }

        /*
         * SUBSCRIBE:
         *
         * Permit only the user's private
         * notification destination.
         */
        if (command == StompCommand.SUBSCRIBE) {

            if (!ALLOWED_SUBSCRIPTION_DESTINATION.equals(
                    accessor.getDestination()
            )) {
                throw new MessagingException(
                        "WebSocket subscription destination is not permitted"
                );
            }

            validateActiveSession(accessor);

            return message;
        }

        /*
         * UNSUBSCRIBE:
         *
         * Only an authenticated, registered
         * session can unsubscribe.
         *
         * The subscription itself is managed
         * by Spring's STOMP message broker.
         */
        if (command == StompCommand.UNSUBSCRIBE) {
            validateRegisteredSession(accessor);
            return message;
        }

        /*
         * DISCONNECT:
         *
         * Allow a client to disconnect even
         * after its JWT expires or its account
         * becomes inactive.
         *
         * Spring publishes SessionDisconnectEvent
         * and the cleanup listener removes the
         * registered session.
         */
        if (command == StompCommand.DISCONNECT) {
            return message;
        }

        /*
         * Unsupported commands are rejected.
         *
         * This application does not currently
         * support client ACK, NACK, or STOMP
         * transaction commands.
         */
        throw new MessagingException(
                "WebSocket STOMP command is not permitted"
        );
    }

    /*
     * Authenticate the initial STOMP CONNECT.
     */
    private Message<?> authenticateConnect(
            Message<?> message,
            StompHeaderAccessor accessor
    ) {

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
                        .substring(BEARER_PREFIX.length())
                        .trim();

        if (token.isBlank()) {
            throw new MessagingException(
                    "Missing WebSocket authorization token"
            );
        }

        try {

            /*
             * Validate the JWT signature,
             * expiry and configured claims.
             */
            Jwt jwt = jwtDecoder.decode(token);

            if (jwt == null) {
                throw new MessagingException(
                        "WebSocket authentication failed"
                );
            }

            /*
             * Resolve the current user,
             * account status and roles.
             */
            Authentication authentication =
                    jwtAuthenticationConverter.convert(jwt);

            if (!(authentication
                    instanceof JwtAuthenticationToken jwtAuthentication)
                    || !jwtAuthentication.isAuthenticated()) {

                throw new MessagingException(
                        "WebSocket authentication failed"
                );
            }

            String sessionId = requireSessionId(accessor);

            /*
             * Bind the authenticated Principal
             * to the STOMP connection.
             */
            accessor.setUser(jwtAuthentication);

            /*
             * Register the session for later
             * subscription and outbound checks.
             */
            sessionRegistry.register(
                    sessionId,
                    jwtAuthentication
            );

            return message;

        } catch (JwtException exception) {
            throw new MessagingException(
                    "Invalid or expired WebSocket token",
                    exception
            );

        } catch (AuthenticationException exception) {
            throw new MessagingException(
                    "WebSocket account authentication failed",
                    exception
            );
        }
    }

    /*
     * Confirm that an existing session still
     * belongs to its original JWT identity.
     *
     * Used for SUBSCRIBE and UNSUBSCRIBE.
     */
    private JwtAuthenticationToken validateRegisteredSession(
            StompHeaderAccessor accessor
    ) {

        if (!(accessor.getUser()
                instanceof JwtAuthenticationToken sessionAuthentication)
                || !sessionAuthentication.isAuthenticated()) {

            throw new MessagingException(
                    "WebSocket session requires JWT authentication"
            );
        }

        String sessionId = requireSessionId(accessor);

        JwtAuthenticationToken registeredAuthentication =
                sessionRegistry.find(sessionId)
                        .orElseThrow(
                                () -> new MessagingException(
                                        "WebSocket session is not registered"
                                )
                        );

        if (!registeredAuthentication.isAuthenticated()) {
            throw new MessagingException(
                    "WebSocket session is not authenticated"
            );
        }

        Jwt registeredJwt =
                registeredAuthentication.getToken();

        Jwt sessionJwt =
                sessionAuthentication.getToken();

        if (registeredJwt == null || sessionJwt == null) {
            throw new MessagingException(
                    "WebSocket session token is missing"
            );
        }

        String registeredTokenValue =
                registeredJwt.getTokenValue();

        String sessionTokenValue =
                sessionJwt.getTokenValue();

        if (registeredTokenValue == null
                || registeredTokenValue.isBlank()
                || !registeredTokenValue.equals(sessionTokenValue)
                || !registeredAuthentication.getName()
                        .equals(sessionAuthentication.getName())) {

            throw new MessagingException(
                    "WebSocket session identity mismatch"
            );
        }

        return registeredAuthentication;
    }

    /*
     * Revalidate JWT and current account
     * authorization for a SUBSCRIBE request.
     */
    private void validateActiveSession(
            StompHeaderAccessor accessor
    ) {

        JwtAuthenticationToken registeredAuthentication =
                validateRegisteredSession(accessor);

        try {

            Jwt validatedJwt =
                    jwtDecoder.decode(
                            registeredAuthentication
                                    .getToken()
                                    .getTokenValue()
                    );

            if (validatedJwt == null) {
                throw new MessagingException(
                        "WebSocket session authentication failed"
                );
            }

            Authentication currentAuthentication =
                    jwtAuthenticationConverter.convert(
                            validatedJwt
                    );

            if (!(currentAuthentication
                    instanceof JwtAuthenticationToken currentJwtAuthentication)
                    || !currentJwtAuthentication.isAuthenticated()) {

                throw new MessagingException(
                        "WebSocket account authentication failed"
                );
            }

            if (!registeredAuthentication.getName()
                    .equals(currentJwtAuthentication.getName())) {

                throw new MessagingException(
                        "WebSocket session identity mismatch"
                );
            }

            Jwt currentJwt =
                    currentJwtAuthentication.getToken();

            if (currentJwt == null
                    || !registeredAuthentication
                            .getToken()
                            .getTokenValue()
                            .equals(currentJwt.getTokenValue())) {

                throw new MessagingException(
                        "WebSocket session token mismatch"
                );
            }

        } catch (JwtException
                | AuthenticationException exception) {

            throw new MessagingException(
                    "WebSocket session authentication is no longer valid",
                    exception
            );
        }
    }

    /*
     * Obtain the server-associated WebSocket
     * session ID.
     */
    private String requireSessionId(
            StompHeaderAccessor accessor
    ) {

        String sessionId = accessor.getSessionId();

        if (sessionId == null || sessionId.isBlank()) {
            throw new MessagingException(
                    "WebSocket session ID is missing"
            );
        }

        return sessionId;
    }
}
