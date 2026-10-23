
package com.flowdesk.notification.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketSessionRegistryTest {

    private WebSocketSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry();
    }

    /*
     * TEST 1
     *
     * A successfully authenticated session
     * should be registered.
     */
    @Test
    void shouldRegisterAuthenticatedSession() {

        JwtAuthenticationToken authentication =
                createAuthentication("valid-token");

        registry.register(
                "session-123",
                authentication
        );

        assertSame(
                authentication,
                registry.find("session-123").orElseThrow()
        );
    }

    /*
     * TEST 2
     *
     * An unknown session must not have
     * an authenticated identity.
     */
    @Test
    void unknownSessionMustNotBeAuthenticated() {

        assertTrue(
                registry.find("unknown-session").isEmpty()
        );
    }

    /*
     * TEST 3
     *
     * Disconnect must remove the
     * session's stored authentication.
     */
    @Test
    void disconnectMustRemoveSession() {

        registry.register(
                "session-123",
                createAuthentication("valid-token")
        );

        registry.remove("session-123");

        assertTrue(
                registry.find("session-123").isEmpty()
        );
    }

    /*
     * TEST 4
     *
     * Removing one session must not remove
     * another active session.
     */
    @Test
    void removingOneSessionMustNotAffectOthers() {

        registry.register(
                "session-A",
                createAuthentication("token-A")
        );

        registry.register(
                "session-B",
                createAuthentication("token-B")
        );

        registry.remove("session-A");

        assertTrue(
                registry.find("session-A").isEmpty()
        );

        assertTrue(
                registry.find("session-B").isPresent()
        );
    }

    /*
     * TEST 5
     *
     * Missing session information must
     * never create a registry entry.
     */
    @Test
    void nullSessionIdMustBeRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(
                        null,
                        createAuthentication("valid-token")
                )
        );
    }

    /*
     * TEST 6
     *
     * Unauthenticated identities must
     * not enter the session registry.
     */
    @Test
    void nullAuthenticationMustBeRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(
                        "session-123",
                        null
                )
        );
    }

    private JwtAuthenticationToken createAuthentication(
            String tokenValue
    ) {

        Jwt jwt = Jwt.withTokenValue(tokenValue)
                .header("alg", "HS256")
                .subject("user-123")
                .build();

        return new JwtAuthenticationToken(
                jwt,
                List.of()
        );
    }
}
