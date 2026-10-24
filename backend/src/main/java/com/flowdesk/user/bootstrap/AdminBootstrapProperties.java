package com.flowdesk.user.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
@ConfigurationProperties(prefix = "app.bootstrap.admin")
public class AdminBootstrapProperties {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;
    private static final int MAX_NAME_LENGTH = 100;

    private boolean enabled = false;
    private String email;
    private String password;
    private String firstName = "System";
    private String lastName = "Administrator";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void validate() {
        if (!enabled) {
            return;
        }

        if (email == null || email.isBlank()) {
            throw new AdminBootstrapException("Bootstrap admin email is required when bootstrap is enabled");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new AdminBootstrapException("Bootstrap admin email is invalid");
        }

        if (password == null || password.isBlank()) {
            throw new AdminBootstrapException("Bootstrap admin password is required when bootstrap is enabled");
        }

        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new AdminBootstrapException(
                    "Bootstrap admin password must be between " + MIN_PASSWORD_LENGTH + " and " + MAX_PASSWORD_LENGTH + " characters"
            );
        }

        if (firstName == null || firstName.isBlank()) {
            throw new AdminBootstrapException("Bootstrap admin first name must not be blank");
        }
        if (firstName.trim().length() > MAX_NAME_LENGTH) {
            throw new AdminBootstrapException("Bootstrap admin first name must not exceed " + MAX_NAME_LENGTH + " characters");
        }

        if (lastName == null || lastName.isBlank()) {
            throw new AdminBootstrapException("Bootstrap admin last name must not be blank");
        }
        if (lastName.trim().length() > MAX_NAME_LENGTH) {
            throw new AdminBootstrapException("Bootstrap admin last name must not exceed " + MAX_NAME_LENGTH + " characters");
        }
    }
}
