package com.flowdesk.auth.security;

import com.flowdesk.auth.config.RefreshTokenProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class RefreshTokenCookieHelper {

    private final RefreshTokenProperties properties;

    public RefreshTokenCookieHelper(RefreshTokenProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie createRefreshCookie(String rawToken, boolean isSecureRequest) {
        boolean secure = properties.isCookieSecure() || isSecureRequest;
        return ResponseCookie.from(properties.getCookieName(), rawToken)
                .httpOnly(true)
                .secure(secure)
                .path(properties.getCookiePath())
                .maxAge(properties.getIdleTtl())
                .sameSite(properties.getSameSite())
                .build();
    }

    public ResponseCookie clearRefreshCookie(boolean isSecureRequest) {
        boolean secure = properties.isCookieSecure() || isSecureRequest;
        return ResponseCookie.from(properties.getCookieName(), "")
                .httpOnly(true)
                .secure(secure)
                .path(properties.getCookiePath())
                .maxAge(0)
                .sameSite(properties.getSameSite())
                .build();
    }

    public String extractRefreshToken(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> properties.getCookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(val -> val != null && !val.isBlank())
                .findFirst()
                .orElse(null);
    }
}

