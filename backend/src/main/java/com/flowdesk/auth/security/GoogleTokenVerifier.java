package com.flowdesk.auth.security;

import com.flowdesk.auth.config.GoogleOAuthProperties;
import com.flowdesk.auth.dto.GoogleUserInfo;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.text.ParseException;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    private static final List<String> ACCEPTED_ISSUERS = List.of(
            "https://accounts.google.com",
            "accounts.google.com"
    );

    private final GoogleOAuthProperties properties;
    private final RestClient restClient;
    private final JWKSource<SecurityContext> jwkSource;

    @Autowired
    public GoogleTokenVerifier(GoogleOAuthProperties properties) {
        this(properties, createDefaultJwkSource(properties));
    }

    public GoogleTokenVerifier(GoogleOAuthProperties properties, JWKSource<SecurityContext> jwkSource) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.jwkSource = jwkSource;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    private static JWKSource<SecurityContext> createDefaultJwkSource(GoogleOAuthProperties properties) {
        try {
            if (properties != null && properties.getJwkSetUri() != null && !properties.getJwkSetUri().isBlank()) {
                return new RemoteJWKSet<>(URI.create(properties.getJwkSetUri().trim()).toURL());
            }
        } catch (Exception e) {
            log.warn("Failed to initialize Google remote JWK source: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Exchanges an authorization code with Google and validates the returned ID token.
     */
    public GoogleUserInfo exchangeCodeAndVerify(String code, String redirectUri) {
        return exchangeCodeAndVerify(code, redirectUri, null);
    }

    /**
     * Exchanges an authorization code with Google (with optional PKCE code_verifier) and validates the returned ID token.
     */
    public GoogleUserInfo exchangeCodeAndVerify(String code, String redirectUri, String codeVerifier) {
        Objects.requireNonNull(code, "Authorization code must not be null");

        if (!properties.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google OAuth 2.0 is not configured on this server");
        }

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("code", code.trim());
        formData.add("client_id", properties.getClientId());
        formData.add("client_secret", properties.getClientSecret());
        formData.add("redirect_uri", properties.getRedirectUri());
        formData.add("grant_type", "authorization_code");

        if (codeVerifier != null && !codeVerifier.isBlank()) {
            formData.add("code_verifier", codeVerifier.trim());
        }

        try {
            Map<String, Object> tokenResponse = restClient.post()
                    .uri(properties.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (tokenResponse == null || !tokenResponse.containsKey("id_token")) {
                log.warn("Google token exchange did not return an id_token");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Failed to obtain identity token from Google");
            }

            String idToken = (String) tokenResponse.get("id_token");
            return verifyIdToken(idToken);

        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            log.error("Google token exchange failed: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google authentication exchange failed");
        }
    }

    /**
     * Validates a Google ID token and returns the verified user information.
     */
    public GoogleUserInfo verifyIdToken(String rawIdToken) {
        if (rawIdToken == null || rawIdToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google ID token is required");
        }

        try {
            SignedJWT signedJwt = SignedJWT.parse(rawIdToken.trim());

            // 0. Cryptographic JWS Signature Verification
            if (jwkSource != null) {
                ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
                JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(
                        JWSAlgorithm.RS256,
                        jwkSource
                );
                jwtProcessor.setJWSKeySelector(keySelector);
                try {
                    jwtProcessor.process(signedJwt, null);
                } catch (BadJOSEException | JOSEException e) {
                    if (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("expired")) {
                        log.warn("Rejected expired Google ID token: {}", e.getMessage());
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google ID token has expired");
                    }
                    log.warn("Google ID token signature verification failed: {}", e.getMessage());
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token signature");
                }
            }

            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();

            // 1. Validate issuer
            String issuer = claims.getIssuer();
            if (issuer == null || !ACCEPTED_ISSUERS.contains(issuer)) {
                log.warn("Rejected Google ID token with untrusted issuer: [{}]", issuer);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token issuer");
            }

            // 2. Validate audience
            List<String> audience = claims.getAudience();
            String expectedClientId = properties.getClientId();
            if (!expectedClientId.isBlank()) {
                if (audience == null || !audience.contains(expectedClientId)) {
                    log.warn("Rejected Google ID token with audience [{}] not matching expected client id [{}]",
                            audience, expectedClientId);
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token audience");
                }
            }

            // 3. Validate expiration
            Date expirationTime = claims.getExpirationTime();
            Date now = new Date();
            // Allow 60 seconds clock skew
            Date skewTolerance = new Date(now.getTime() - 60000);
            if (expirationTime == null || expirationTime.before(skewTolerance)) {
                log.warn("Rejected expired Google ID token (expired at [{}])", expirationTime);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google ID token has expired");
            }

            // 4. Validate subject
            String sub = claims.getSubject();
            if (sub == null || sub.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing subject identifier in Google ID token");
            }

            // 5. Validate verified-email claim
            Object emailVerifiedObj = claims.getClaim("email_verified");
            boolean emailVerified = Boolean.TRUE.equals(emailVerifiedObj)
                    || "true".equalsIgnoreCase(String.valueOf(emailVerifiedObj));

            if (!emailVerified) {
                log.warn("Rejected Google ID token for subject [{}] because email is not verified by Google", sub);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google email address is not verified");
            }

            String email = (String) claims.getClaim("email");
            if (email == null || email.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing email address in Google ID token");
            }

            String name = (String) claims.getClaim("name");
            if (name == null || name.isBlank()) {
                name = (String) claims.getClaim("given_name");
            }

            return new GoogleUserInfo(
                    sub.trim(),
                    email.trim().toLowerCase(Locale.ROOT),
                    name != null ? name.trim() : "Google User",
                    emailVerified
            );

        } catch (ParseException e) {
            log.warn("Malformed Google ID token: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed Google ID token");
        }
    }
}
