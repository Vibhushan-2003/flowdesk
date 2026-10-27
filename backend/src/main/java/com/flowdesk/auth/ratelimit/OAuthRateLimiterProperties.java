package com.flowdesk.auth.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security.rate-limit.oauth")
public class OAuthRateLimiterProperties {

    private boolean enabled = true;
    private int ipMaxAttempts = 30;
    private long ipWindowSeconds = 60;
    private int maxTrackedKeys = 10000;
    private int maxBodyBytes = 8192;
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
            throw new IllegalArgumentException("app.security.rate-limit.oauth.ip-max-attempts must be > 0");
        }
        if (ipWindowSeconds <= 0) {
            throw new IllegalArgumentException("app.security.rate-limit.oauth.ip-window-seconds must be > 0");
        }
        if (maxTrackedKeys <= 0) {
            throw new IllegalArgumentException("app.security.rate-limit.oauth.max-tracked-keys must be > 0");
        }
        if (maxBodyBytes <= 0) {
            throw new IllegalArgumentException("app.security.rate-limit.oauth.max-body-bytes must be > 0");
        }
    }
}
