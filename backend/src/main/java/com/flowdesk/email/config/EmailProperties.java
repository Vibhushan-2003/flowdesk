package com.flowdesk.email.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.util.regex.Pattern;

/**
 * Configuration properties for FlowDesk outbound email delivery.
 * <p>
 * Supports both local development (e.g. Mailpit at localhost:1025) and authenticated
 * TLS/SSL production SMTP relays (e.g. Amazon SES, SendGrid, Mailgun).
 */
@ConfigurationProperties(prefix = "app.mail")
public class EmailProperties implements EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(EmailProperties.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,15}$"
    );

    private static final int MAX_TIMEOUT_MS = 60000;

    private Environment environment;

    /**
     * Master switch for outbound email delivery. If false, emails are safely suppressed.
     */
    private boolean enabled = true;

    /**
     * SMTP server host (defaults to localhost for local development / Mailpit).
     */
    private String host = "localhost";

    /**
     * SMTP server port (defaults to 1025 for Mailpit).
     */
    private int port = 1025;

    /**
     * SMTP username (leave blank for unauthenticated local development).
     */
    private String username = "";

    /**
     * SMTP password (leave blank for unauthenticated local development).
     */
    private String password = "";

    /**
     * Whether SMTP authentication is required.
     */
    private boolean auth = false;

    /**
     * Whether STARTTLS is enabled (required for most cloud SMTP providers on port 587).
     */
    private boolean starttls = false;

    /**
     * Whether SSL/TLS is enabled directly (typically for port 465).
     */
    private boolean ssl = false;

    /**
     * Socket connection timeout in milliseconds (bounded to avoid hanging threads).
     */
    private int connectionTimeoutMs = 5000;

    /**
     * Socket read timeout in milliseconds.
     */
    private int timeoutMs = 5000;

    /**
     * Socket write timeout in milliseconds.
     */
    private int writeTimeoutMs = 5000;

    /**
     * Outbound 'From' email address.
     */
    private String fromAddress = "noreply@flowdesk.internal";

    /**
     * Display name for outbound sender.
     */
    private String fromName = "FlowDesk";

    /**
     * Flag indicating production environment. When true, strict validation prevents
     * dangerous unencrypted or localhost defaults.
     */
    private boolean production = false;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (!enabled) {
            log.info("FlowDesk email subsystem is disabled by configuration (app.mail.enabled=false).");
            return;
        }

        if (port < 1 || port > 65535) {
            throw new IllegalStateException("app.mail.port must be between 1 and 65535: " + port);
        }

        if (fromAddress == null || !EMAIL_PATTERN.matcher(fromAddress.trim()).matches()) {
            throw new IllegalStateException("app.mail.from-address must be a valid email address: " + fromAddress);
        }

        if (fromName != null && (fromName.indexOf('\r') >= 0 || fromName.indexOf('\n') >= 0)) {
            throw new IllegalStateException("app.mail.from-name contains illegal newline characters.");
        }

        if (connectionTimeoutMs <= 0 || timeoutMs <= 0 || writeTimeoutMs <= 0) {
            throw new IllegalStateException("Email timeouts must be strictly positive milliseconds.");
        }

        if (connectionTimeoutMs > MAX_TIMEOUT_MS || timeoutMs > MAX_TIMEOUT_MS || writeTimeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalStateException("Email timeouts must not exceed " + MAX_TIMEOUT_MS + " ms to prevent thread starvation.");
        }

        if (isProduction()) {
            if (host == null || host.isBlank() || isLocalhost(host)) {
                throw new IllegalStateException("Production email configuration requires a non-localhost SMTP host.");
            }
            if (!starttls && !ssl) {
                throw new IllegalStateException("Production email configuration requires STARTTLS or SSL/TLS encryption. Unencrypted SMTP delivery is forbidden in production.");
            }
            if (auth && (username == null || username.isBlank() || password == null || password.isBlank())) {
                throw new IllegalStateException("Production SMTP authentication is enabled but username/password credentials are missing.");
            }
            log.info("FlowDesk production email subsystem configured for host: {}:{} (auth={}, starttls={}, ssl={})",
                    host, port, auth, starttls, ssl);
        } else {
            log.info("FlowDesk local development email subsystem configured for {}:{} (Mailpit mode, TLS/auth={})",
                    host, port, auth);
        }
    }

    private static boolean isLocalhost(String host) {
        if (host == null) {
            return false;
        }
        String trimmed = host.trim().toLowerCase();
        return "localhost".equals(trimmed)
                || "127.0.0.1".equals(trimmed)
                || "::1".equals(trimmed)
                || "0.0.0.0".equals(trimmed)
                || trimmed.endsWith(".localhost")
                || "localhost.localdomain".equals(trimmed);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isAuth() {
        return auth;
    }

    public void setAuth(boolean auth) {
        this.auth = auth;
    }

    public boolean isStarttls() {
        return starttls;
    }

    public void setStarttls(boolean starttls) {
        this.starttls = starttls;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getWriteTimeoutMs() {
        return writeTimeoutMs;
    }

    public void setWriteTimeoutMs(int writeTimeoutMs) {
        this.writeTimeoutMs = writeTimeoutMs;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
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
}
