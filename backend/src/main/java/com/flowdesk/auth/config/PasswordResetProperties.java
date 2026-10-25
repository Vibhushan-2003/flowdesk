package com.flowdesk.auth.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for secure password recovery.
 */
@Component
@ConfigurationProperties(prefix = "app.security.password-reset")
public class PasswordResetProperties implements EnvironmentAware {

    private Environment environment;

    /**
     * Frontend base origin used for constructing secure password reset links.
     * Never derived from untrusted request headers (e.g. Host, X-Forwarded-Host).
     */
    private String frontendOrigin = "http://localhost:5173";

    /**
     * Expiration duration for password reset tokens in minutes. Default 15 minutes.
     */
    private int tokenValidityMinutes = 15;

    /**
     * Entropy in bytes for cryptographically secure reset tokens (minimum 32 bytes = 256 bits).
     */
    private int tokenEntropyBytes = 32;

    /**
     * Explicit production flag. If omitted, active Spring profiles are inspected.
     */
    private boolean production = false;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (frontendOrigin == null || frontendOrigin.isBlank()) {
            throw new IllegalStateException("app.security.password-reset.frontend-origin must not be blank.");
        }

        if (tokenValidityMinutes <= 0) {
            throw new IllegalStateException("app.security.password-reset.token-validity-minutes must be positive.");
        }

        if (tokenEntropyBytes < 32) {
            throw new IllegalStateException("app.security.password-reset.token-entropy-bytes must be at least 32 (256 bits).");
        }

        if (isProduction()) {
            if (!frontendOrigin.startsWith("https://")) {
                throw new IllegalStateException("Production password reset requires an HTTPS frontend origin: " + frontendOrigin);
            }
        }
    }

    public boolean isProduction() {
        if (production) {
            return true;
        }
        if (environment != null) {
            for (String profile : environment.getActiveProfiles()) {
                if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getFrontendOrigin() {
        return frontendOrigin.replaceAll("/+$", "");
    }

    public void setFrontendOrigin(String frontendOrigin) {
        this.frontendOrigin = frontendOrigin;
    }

    public int getTokenValidityMinutes() {
        return tokenValidityMinutes;
    }

    public Duration getTokenValidityDuration() {
        return Duration.ofMinutes(tokenValidityMinutes);
    }

    public void setTokenValidityMinutes(int tokenValidityMinutes) {
        this.tokenValidityMinutes = tokenValidityMinutes;
    }

    public int getTokenEntropyBytes() {
        return tokenEntropyBytes;
    }

    public void setTokenEntropyBytes(int tokenEntropyBytes) {
        this.tokenEntropyBytes = tokenEntropyBytes;
    }

    public void setProduction(boolean production) {
        this.production = production;
    }
}
