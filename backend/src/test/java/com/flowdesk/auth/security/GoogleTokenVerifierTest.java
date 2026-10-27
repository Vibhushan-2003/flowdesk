package com.flowdesk.auth.security;

import com.flowdesk.auth.config.GoogleOAuthProperties;
import com.flowdesk.auth.dto.GoogleUserInfo;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "test-client.apps.googleusercontent.com";
    private static final String KEY_ID = "google-test-key-1";

    private RSAKey rsaKey;
    private RSAKey untrustedRsaKey;
    private JWKSource<SecurityContext> jwkSource;
    private GoogleOAuthProperties properties;
    private GoogleTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = new RSAKeyGenerator(2048)
                .keyID(KEY_ID)
                .algorithm(JWSAlgorithm.RS256)
                .generate();

        untrustedRsaKey = new RSAKeyGenerator(2048)
                .keyID("untrusted-key-id")
                .algorithm(JWSAlgorithm.RS256)
                .generate();

        jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));

        properties = new GoogleOAuthProperties();
        properties.setClientId(CLIENT_ID);
        verifier = new GoogleTokenVerifier(properties, jwkSource);
    }

    private String createSignedToken(JWTClaimsSet claims) throws Exception {
        return createSignedTokenWithKey(claims, rsaKey);
    }

    private String createSignedTokenWithKey(JWTClaimsSet claims, RSAKey signingKey) throws Exception {
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                claims
        );
        signedJWT.sign(new RSASSASigner(signingKey));
        return signedJWT.serialize();
    }

    @Test
    @DisplayName("verifyIdToken successfully extracts claims from valid Google ID token with verified signature")
    void verifyIdToken_validToken_succeeds() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .subject("google-sub-12345")
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .claim("email", "john.doe@gmail.com")
                .claim("email_verified", true)
                .claim("name", "John Doe")
                .build();

        String rawToken = createSignedToken(claims);

        GoogleUserInfo userInfo = verifier.verifyIdToken(rawToken);

        assertThat(userInfo.sub()).isEqualTo("google-sub-12345");
        assertThat(userInfo.email()).isEqualTo("john.doe@gmail.com");
        assertThat(userInfo.name()).isEqualTo("John Doe");
        assertThat(userInfo.emailVerified()).isTrue();
    }

    @Test
    @DisplayName("verifyIdToken accepts alternate issuer 'accounts.google.com'")
    void verifyIdToken_alternateIssuer_succeeds() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("accounts.google.com")
                .audience(CLIENT_ID)
                .subject("google-sub-67890")
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .claim("email", "jane.doe@gmail.com")
                .claim("email_verified", "true")
                .build();

        String rawToken = createSignedToken(claims);

        GoogleUserInfo userInfo = verifier.verifyIdToken(rawToken);

        assertThat(userInfo.sub()).isEqualTo("google-sub-67890");
        assertThat(userInfo.email()).isEqualTo("jane.doe@gmail.com");
    }

    @Test
    @DisplayName("verifyIdToken rejects token signed by untrusted RSA key")
    void verifyIdToken_untrustedKeySignature_throwsUnauthorized() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .subject("google-sub-12345")
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .claim("email", "john.doe@gmail.com")
                .claim("email_verified", true)
                .build();

        String rawToken = createSignedTokenWithKey(claims, untrustedRsaKey);

        assertThatThrownBy(() -> verifier.verifyIdToken(rawToken))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).contains("Invalid token signature");
                });
    }

    @Test
    @DisplayName("verifyIdToken rejects token with tampered payload")
    void verifyIdToken_tamperedPayload_throwsUnauthorized() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .subject("google-sub-12345")
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .claim("email", "john.doe@gmail.com")
                .claim("email_verified", true)
                .build();

        String rawToken = createSignedToken(claims);
        String[] parts = rawToken.split("\\.");
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"attacker\"}".getBytes(StandardCharsets.UTF_8));
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThatThrownBy(() -> verifier.verifyIdToken(tamperedToken))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).contains("Invalid token signature");
                });
    }

    @Test
    @DisplayName("verifyIdToken rejects untrusted issuer")
    void verifyIdToken_untrustedIssuer_throwsUnauthorized() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://evil.issuer.com")
                .audience(CLIENT_ID)
                .subject("sub-1")
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .claim("email", "hacker@evil.com")
                .claim("email_verified", true)
                .build();

        String rawToken = createSignedToken(claims);

        assertThatThrownBy(() -> verifier.verifyIdToken(rawToken))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).contains("Invalid token issuer");
                });
    }

    @Test
    @DisplayName("verifyIdToken rejects mismatched audience")
    void verifyIdToken_mismatchedAudience_throwsUnauthorized() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience("another-app.apps.googleusercontent.com")
                .subject("sub-1")
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .claim("email", "user@gmail.com")
                .claim("email_verified", true)
                .build();

        String rawToken = createSignedToken(claims);

        assertThatThrownBy(() -> verifier.verifyIdToken(rawToken))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).contains("Invalid token audience");
                });
    }

    @Test
    @DisplayName("verifyIdToken rejects expired token")
    void verifyIdToken_expiredToken_throwsUnauthorized() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .subject("sub-1")
                // Expired 5 minutes ago
                .expirationTime(new Date(System.currentTimeMillis() - 300_000))
                .claim("email", "user@gmail.com")
                .claim("email_verified", true)
                .build();

        String rawToken = createSignedToken(claims);

        assertThatThrownBy(() -> verifier.verifyIdToken(rawToken))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).contains("expired");
                });
    }

    @Test
    @DisplayName("verifyIdToken rejects unverified email (email_verified is false)")
    void verifyIdToken_unverifiedEmail_throwsBadRequest() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .subject("sub-1")
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .claim("email", "unverified@gmail.com")
                .claim("email_verified", false)
                .build();

        String rawToken = createSignedToken(claims);

        assertThatThrownBy(() -> verifier.verifyIdToken(rawToken))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("not verified");
                });
    }

    @Test
    @DisplayName("verifyIdToken rejects missing subject claim")
    void verifyIdToken_missingSubject_throwsUnauthorized() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .claim("email", "user@gmail.com")
                .claim("email_verified", true)
                .build();

        String rawToken = createSignedToken(claims);

        assertThatThrownBy(() -> verifier.verifyIdToken(rawToken))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).contains("Missing subject identifier");
                });
    }

    @Test
    @DisplayName("verifyIdToken rejects malformed JWT string")
    void verifyIdToken_malformedToken_throwsBadRequest() {
        assertThatThrownBy(() -> verifier.verifyIdToken("not.a.valid.jwt"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("Malformed Google ID token");
                });
    }
}
