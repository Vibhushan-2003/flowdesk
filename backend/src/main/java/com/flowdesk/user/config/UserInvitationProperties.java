package com.flowdesk.user.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.security.invitation")
public class UserInvitationProperties implements EnvironmentAware {

    private Environment environment;

    private String frontendOrigin = "http://localhost:5173";
    private int tokenValidityHours = 48;
    private int tokenEntropyBytes = 32;
    private boolean production = false;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public String getFrontendOrigin() {
        if (frontendOrigin == null) {
            return "";
        }
        return frontendOrigin.replaceAll("/+$", "");
    }

    public void setFrontendOrigin(String frontendOrigin) {
        this.frontendOrigin = frontendOrigin;
    }

    public int getTokenValidityHours() {
        return tokenValidityHours;
    }

    public void setTokenValidityHours(int tokenValidityHours) {
        this.tokenValidityHours = tokenValidityHours;
    }

    public Duration getTokenValidityDuration() {
        return Duration.ofHours(tokenValidityHours);
    }

    public int getTokenEntropyBytes() {
        return tokenEntropyBytes;
    }

    public void setTokenEntropyBytes(int tokenEntropyBytes) {
        this.tokenEntropyBytes = tokenEntropyBytes;
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

    public void setProduction(boolean production) {
        this.production = production;
    }

    @PostConstruct
    public void validate() {
        if (frontendOrigin == null || frontendOrigin.isBlank()) {
            throw new IllegalStateException("app.security.invitation.frontend-origin must not be blank.");
        }
        if (tokenValidityHours <= 0 || tokenValidityHours > 720) {
            throw new IllegalStateException("app.security.invitation.token-validity-hours must be between 1 and 720.");
        }
        if (tokenEntropyBytes < 32) {
            throw new IllegalStateException("app.security.invitation.token-entropy-bytes must be >= 32 (256 bits).");
        }
        if (isProduction()) {
            if (!frontendOrigin.startsWith("https://")) {
                throw new IllegalStateException("Production invitation requires an HTTPS frontend origin: " + frontendOrigin);
            }
        }
    }
}
