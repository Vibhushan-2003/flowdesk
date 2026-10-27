package com.flowdesk.auth.exception;

import java.util.Objects;

public class OAuthLinkingRequiredException extends RuntimeException {

    private final String linkToken;
    private final String email;
    private final String provider;

    public OAuthLinkingRequiredException(String linkToken, String email, String provider) {
        super("An existing FlowDesk account was found for " + email + ". Please enter your FlowDesk password to securely link your " + provider + " account.");
        this.linkToken = Objects.requireNonNull(linkToken, "linkToken must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
    }

    public String getLinkToken() {
        return linkToken;
    }

    public String getEmail() {
        return email;
    }

    public String getProvider() {
        return provider;
    }
}
