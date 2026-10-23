
package com.flowdesk.notification.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import org.springframework.stereotype.Component;

@Component
public class WebSocketOutboundSecurityInterceptor
        implements ChannelInterceptor {

    private static final String NOTIFICATION_DESTINATION =
            "/queue/notifications";

    private final JwtDecoder jwtDecoder;

    private final JwtAuthenticationConverter authenticationConverter;

    private final WebSocketSessionRegistry sessionRegistry;

    public WebSocketOutboundSecurityInterceptor(
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter authenticationConverter,
            WebSocketSessionRegistry sessionRegistry
    ) {
        this.jwtDecoder = jwtDecoder;
        this.authenticationConverter = authenticationConverter;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {

        /*
         * STEP 1:
         *
         * Only inspect outbound MESSAGE frames.
         *
         * CONNECT_ACK, DISCONNECT_ACK, heartbeats,
         * and other control frames are unaffected.
         */
        SimpMessageType messageType =
                SimpMessageHeaderAccessor.getMessageType(
                        message.getHeaders()
                );

        if (messageType != SimpMessageType.MESSAGE) {
            return message;
        }

        /*
         * STEP 2:
         *
         * Only protect notification destinations.
         *
         * Other features must not be blocked by
         * this notification-specific interceptor.
         */
        String destination =
                SimpMessageHeaderAccessor.getDestination(
                        message.getHeaders()
                );

        if (!isNotificationDestination(destination)) {
            return message;
        }

        /*
         * STEP 3:
         *
         * The outbound message must identify
         * the intended WebSocket session.
         */
        String sessionId =
                SimpMessageHeaderAccessor.getSessionId(
                        message.getHeaders()
                );

        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }

        /*
         * STEP 4:
         *
         * Find the authentication registered
         * during the original STOMP CONNECT.
         */
        JwtAuthenticationToken registeredAuthentication =
                sessionRegistry.find(sessionId)
                        .orElse(null);

        if (registeredAuthentication == null
                || !registeredAuthentication.isAuthenticated()) {
            return null;
        }

        Jwt registeredJwt =
                registeredAuthentication.getToken();

        if (registeredJwt == null) {
            return null;
        }

        String tokenValue =
                registeredJwt.getTokenValue();

        if (tokenValue == null || tokenValue.isBlank()) {
            return null;
        }

        /*
         * STEP 5:
         *
         * Revalidate the JWT at delivery time.
         *
         * Expired or invalid tokens must not
         * receive private notifications.
         */
        try {

            Jwt currentJwt =
                    jwtDecoder.decode(tokenValue);

            if (currentJwt == null) {
                return null;
            }

            /*
             * STEP 6:
             *
             * Convert the freshly validated JWT
             * into the user's current authentication.
             *
             * The configured converter checks
             * current account status and roles.
             */
            var convertedAuthentication =
                    authenticationConverter.convert(currentJwt);

            if (!(convertedAuthentication
                    instanceof JwtAuthenticationToken currentAuthentication)
                    || !currentAuthentication.isAuthenticated()) {
                return null;
            }

            /*
             * STEP 7:
             *
             * Ensure the session still belongs
             * to the same authenticated user.
             */
            if (!registeredAuthentication.getName()
                    .equals(currentAuthentication.getName())) {
                return null;
            }

            /*
             * STEP 8:
             *
             * Ensure the freshly validated
             * authentication uses the same JWT.
             */
            Jwt currentToken =
                    currentAuthentication.getToken();

            if (currentToken == null
                    || !tokenValue.equals(currentToken.getTokenValue())) {
                return null;
            }

            /*
             * STEP 9:
             *
             * All security checks passed.
             *
             * Allow the private notification
             * to reach the WebSocket client.
             */
            return message;

        } catch (RuntimeException exception) {

            /*
             * FAIL CLOSED:
             *
             * Any JWT or authentication validation
             * error must block notification delivery.
             *
             * Never log sensitive JWT values.
             */
            return null;
        }
    }

    /*
     * Protect the logical notification queue
     * and Spring's resolved notification
     * destinations containing session suffixes.
     */
    private boolean isNotificationDestination(
            String destination
    ) {

        if (destination == null) {
            return false;
        }

        return destination.equals(
                NOTIFICATION_DESTINATION
        ) || destination.startsWith(
                NOTIFICATION_DESTINATION + "-"
        );
    }
}
