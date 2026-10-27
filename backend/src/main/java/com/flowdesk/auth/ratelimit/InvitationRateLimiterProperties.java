package com.flowdesk.auth.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security.rate-limit.invitation")
public class InvitationRateLimiterProperties {

    private boolean enabled = true;
    private int acceptIpMaxAttempts = 15;
    private long acceptIpWindowSeconds = 60;
    private int maxTrackedKeys = 10000;
    private int maxBodyBytes = 4096;
    private boolean trustProxyHeaders = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getAcceptIpMaxAttempts() {
        return acceptIpMaxAttempts;
    }

    public void setAcceptIpMaxAttempts(int acceptIpMaxAttempts) {
        this.acceptIpMaxAttempts = acceptIpMaxAttempts;
    }

    public long getAcceptIpWindowSeconds() {
        return acceptIpWindowSeconds;
    }

    public void setAcceptIpWindowSeconds(long acceptIpWindowSeconds) {
        this.acceptIpWindowSeconds = acceptIpWindowSeconds;
    }

    public int getMaxTrackedKeys() {
        return maxTrackedKeys;
    }

    public void setMaxTrackedKeys(int maxTrackedKeys) {
        this.maxTrackedKeys = maxTrackedKeys;
    }

    public int getMaxBodyBytes() {
        return maxBodyBytes;
    }

    public void setMaxBodyBytes(int maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    public boolean isTrustProxyHeaders() {
        return trustProxyHeaders;
    }

    public void setTrustProxyHeaders(boolean trustProxyHeaders) {
        this.trustProxyHeaders = trustProxyHeaders;
    }

    public void validate() {
        if (acceptIpMaxAttempts <= 0) {
            throw new IllegalArgumentException("app.security.rate-limit.invitation.accept-ip-max-attempts must be > 0");
        }
        if (acceptIpWindowSeconds <= 0) {
            throw new IllegalArgumentException("app.security.rate-limit.invitation.accept-ip-window-seconds must be > 0");
        }
        if (maxTrackedKeys <= 0) {
            throw new IllegalArgumentException("app.security.rate-limit.invitation.max-tracked-keys must be > 0");
        }
        if (maxBodyBytes <= 0) {
            throw new IllegalArgumentException("app.security.rate-limit.invitation.max-body-bytes must be > 0");
        }
    }
}
