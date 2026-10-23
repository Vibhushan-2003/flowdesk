
package com.flowdesk.notification.realtime;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class WebSocketSessionRegistry {

    private final ConcurrentMap<String, JwtAuthenticationToken> sessions =
            new ConcurrentHashMap<>();

    /*
     * Register a session only after successful
     * JWT authentication.
     */
    public void register(
            String sessionId,
            JwtAuthenticationToken authentication
    ) {

        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException(
                    "WebSocket session ID is required"
            );
        }

        if (authentication == null ||
                !authentication.isAuthenticated()) {
            throw new IllegalArgumentException(
                    "Authenticated JWT identity is required"
            );
        }

        sessions.put(sessionId, authentication);
    }

    /*
     * Return the authentication associated
     * with a WebSocket session.
     */
    public Optional<JwtAuthenticationToken> find(
            String sessionId
    ) {

        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                sessions.get(sessionId)
        );
    }

    /*
     * Remove authentication when a WebSocket
     * session disconnects.
     */
    public void remove(String sessionId) {

        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        sessions.remove(sessionId);
    }
}
