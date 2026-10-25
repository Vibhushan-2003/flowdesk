package com.flowdesk.auth.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security.rate-limit.forgot-password")
public class ForgotPasswordRateLimiterProperties {

    private boolean enabled = true;
    private int ipMaxAttempts = 10;
    private long ipWindowSeconds = 60;
    private int ipEmailMaxAttempts = 3;
    private long ipEmailWindowSeconds = 300;
    private int resetPasswordIpMaxAttempts = 15;
    private long resetPasswordIpWindowSeconds = 60;
    private int maxTrackedKeys = 10000;
    private int maxBodyBytes = 4096; // 4 KB payload limit
    private boolean trustProxyHeaders = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getIpMaxAttempts() {
        return ipMaxAttempts;
    }

    public void setIpMaxAttempts(int ipMaxAttempts) {
        this.ipMaxAttempts = ipMaxAttempts;
    }

    public long getIpWindowSeconds() {
        return ipWindowSeconds;
    }

    public void setIpWindowSeconds(long ipWindowSeconds) {
        this.ipWindowSeconds = ipWindowSeconds;
    }

    public int getIpEmailMaxAttempts() {
        return ipEmailMaxAttempts;
    }

    public void setIpEmailMaxAttempts(int ipEmailMaxAttempts) {
        this.ipEmailMaxAttempts = ipEmailMaxAttempts;
    }

    public long getIpEmailWindowSeconds() {
        return ipEmailWindowSeconds;
    }

    public void setIpEmailWindowSeconds(long ipEmailWindowSeconds) {
        this.ipEmailWindowSeconds = ipEmailWindowSeconds;
    }

    public int getResetPasswordIpMaxAttempts() {
        return resetPasswordIpMaxAttempts;
    }

    public void setResetPasswordIpMaxAttempts(int resetPasswordIpMaxAttempts) {
        this.resetPasswordIpMaxAttempts = resetPasswordIpMaxAttempts;
    }

    public long getResetPasswordIpWindowSeconds() {
        return resetPasswordIpWindowSeconds;
    }

    public void setResetPasswordIpWindowSeconds(long resetPasswordIpWindowSeconds) {
        this.resetPasswordIpWindowSeconds = resetPasswordIpWindowSeconds;
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
        if (ipMaxAttempts <= 0) {
            throw new IllegalArgumentException("app.security.rate-limit.forgot-password.ip-max-attempts must be > 0");
        }
        if (ipWindowSeconds <= 0) {
            throw new IllegalArgumentException("app.security.rate-limit.forgot-password.ip-window-seconds must be > 0");
        }
        if (ipEmailMaxAttempts <= 0) {
            throw new IllegalArgumentException("app.security.rate-limit.forgot-password.ip-email-max-attempts must be > 0");
        }
        if (ipEmailWindowSeconds <= 0) {
            throw new IllegalArgumentException("app.security.rate-limit.forgot-password.ip-email-window-seconds must be > 0");
        }
        if (resetPasswordIpMaxAttempts <= 0) {
            throw new IllegalArgumentException("app.security.rate-limit.forgot-password.reset-password-ip-max-attempts must be > 0");
        }
        if (resetPasswordIpWindowSeconds <= 0) {
            throw new IllegalArgumentException("app.security.rate-limit.forgot-password.reset-password-ip-window-seconds must be > 0");
        }
        if (maxTrackedKeys <= 0) {
            throw new IllegalArgumentException("app.security.rate-limit.forgot-password.max-tracked-keys must be > 0");
        }
        if (maxBodyBytes <= 0) {
            throw new IllegalArgumentException("app.security.rate-limit.forgot-password.max-body-bytes must be > 0");
        }
    }
}
