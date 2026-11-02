package com.flowdesk.auth.controller;

import com.flowdesk.auth.dto.AcceptInvitationRequest;
import com.flowdesk.auth.dto.AcceptInvitationResponse;
import com.flowdesk.auth.dto.ChangePasswordRequest;
import com.flowdesk.auth.dto.ChangePasswordResponse;
import com.flowdesk.auth.dto.ForgotPasswordRequest;
import com.flowdesk.auth.dto.ForgotPasswordResponse;
import com.flowdesk.auth.dto.GoogleAuthRequest;
import com.flowdesk.auth.dto.GoogleLinkAccountRequest;
import com.flowdesk.auth.dto.GoogleOAuthConfigResponse;
import com.flowdesk.auth.dto.LoginRequest;
import com.flowdesk.auth.dto.LoginResponse;
import com.flowdesk.auth.dto.MeResponse;
import com.flowdesk.auth.dto.ResetPasswordRequest;
import com.flowdesk.auth.dto.ResetPasswordResponse;
import com.flowdesk.auth.security.RefreshTokenCookieHelper;
import com.flowdesk.auth.service.AuthService;
import com.flowdesk.auth.service.GoogleAuthService;
import com.flowdesk.auth.service.PasswordResetService;
import com.flowdesk.auth.service.RefreshSessionService;
import com.flowdesk.user.service.UserInvitationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Locale;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Value("${app.cors.allowed-origin:http://localhost:5173}")
    private String allowedOrigins = "http://localhost:5173";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final UserInvitationService userInvitationService;
    private final GoogleAuthService googleAuthService;
    private final RefreshSessionService refreshSessionService;
    private final RefreshTokenCookieHelper refreshTokenCookieHelper;

    @Autowired
    public AuthController(
            AuthService authService,
            PasswordResetService passwordResetService,
            UserInvitationService userInvitationService,
            GoogleAuthService googleAuthService,
            RefreshSessionService refreshSessionService,
            RefreshTokenCookieHelper refreshTokenCookieHelper
    ) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.userInvitationService = userInvitationService;
        this.googleAuthService = googleAuthService;
        this.refreshSessionService = refreshSessionService;
        this.refreshTokenCookieHelper = refreshTokenCookieHelper;
    }

    public AuthController(
            AuthService authService,
            PasswordResetService passwordResetService,
            UserInvitationService userInvitationService,
            GoogleAuthService googleAuthService
    ) {
        this(authService, passwordResetService, userInvitationService, googleAuthService, null, null);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String clientIp = extractClientIp(httpRequest);
        String userAgent = httpRequest != null ? httpRequest.getHeader(HttpHeaders.USER_AGENT) : "unknown";

        AuthService.LoginSessionResult sessionResult =
                authService.loginWithSession(request, clientIp, userAgent);

        if (sessionResult == null) {
            LoginResponse fallbackResponse = authService.login(request);
            return ResponseEntity.ok(fallbackResponse);
        }

        if (refreshTokenCookieHelper != null && sessionResult.rawRefreshToken() != null && httpResponse != null) {
            boolean isSecure = isSecureRequest(httpRequest);
            ResponseCookie cookie = refreshTokenCookieHelper.createRefreshCookie(
                    sessionResult.rawRefreshToken(),
                    isSecure
            );
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }

        return ResponseEntity.ok(sessionResult.loginResponse());
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        validateCsrfProtection(httpRequest);

        if (refreshSessionService == null || refreshTokenCookieHelper == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Session management unavailable");
        }

        String rawToken = refreshTokenCookieHelper.extractRefreshToken(httpRequest);
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is missing");
        }

        String clientIp = extractClientIp(httpRequest);
        String userAgent = httpRequest != null ? httpRequest.getHeader(HttpHeaders.USER_AGENT) : "unknown";

        RefreshSessionService.TokenRefreshResult refreshResult =
                refreshSessionService.rotateSession(rawToken, clientIp, userAgent);

        if (refreshResult.newRawToken() != null && httpResponse != null) {
            boolean isSecure = httpRequest != null && httpRequest.isSecure();
            ResponseCookie newCookie = refreshTokenCookieHelper.createRefreshCookie(
                    refreshResult.newRawToken(),
                    isSecure
            );
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, newCookie.toString());
        }

        LoginResponse response = new LoginResponse(
                refreshResult.newAccessToken(),
                "Bearer",
                refreshResult.user().id(),
                refreshResult.user().email(),
                refreshResult.user().roles()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        validateCsrfProtection(httpRequest);

        if (refreshTokenCookieHelper != null && refreshSessionService != null) {
            String rawToken = refreshTokenCookieHelper.extractRefreshToken(httpRequest);
            if (rawToken != null) {
                refreshSessionService.revokeSession(rawToken);
            }
            if (httpResponse != null) {
                boolean isSecure = isSecureRequest(httpRequest);
                ResponseCookie clearCookie = refreshTokenCookieHelper.clearRefreshCookie(isSecure);
                httpResponse.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());
            }
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/oauth2/config")
    public ResponseEntity<GoogleOAuthConfigResponse> getOAuthConfig() {
        return ResponseEntity.ok(
                googleAuthService.getOAuthConfig()
        );
    }

    @PostMapping("/oauth2/google")
    public ResponseEntity<LoginResponse> googleLogin(
            @RequestBody GoogleAuthRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        LoginResponse response = googleAuthService.authenticate(request);
        attachRefreshSession(response, httpRequest, httpResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/oauth2/google/link")
    public ResponseEntity<LoginResponse> completeGoogleLink(
            @Valid @RequestBody GoogleLinkAccountRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        LoginResponse response = googleAuthService.completeLinking(request.linkToken(), request.flowDeskPassword());
        attachRefreshSession(response, httpRequest, httpResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        return ResponseEntity.ok(
                passwordResetService.requestPasswordReset(request)
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        return ResponseEntity.ok(
                passwordResetService.resetPassword(request)
        );
    }

    @PostMapping("/accept-invitation")
    public ResponseEntity<AcceptInvitationResponse> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest request
    ) {
        return ResponseEntity.ok(
                userInvitationService.acceptInvitation(request)
        );
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString("email");

        List<String> roles = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .sorted()
                .toList();

        return ResponseEntity.ok(
                new MeResponse(
                        userId,
                        email,
                        roles
                )
        );
    }

    @PostMapping("/change-password")
    public ResponseEntity<ChangePasswordResponse> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        return ResponseEntity.ok(
                authService.changePassword(userId, request)
        );
    }

    private void attachRefreshSession(
            LoginResponse response,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        if (authService != null && refreshTokenCookieHelper != null && response != null && response.userId() != null && httpResponse != null) {
            String clientIp = extractClientIp(httpRequest);
            String userAgent = httpRequest != null ? httpRequest.getHeader(HttpHeaders.USER_AGENT) : "unknown";
            String rawRefreshToken = authService.createSessionForUser(response.userId(), clientIp, userAgent);
            if (rawRefreshToken != null) {
                boolean isSecure = isSecureRequest(httpRequest);
                ResponseCookie cookie = refreshTokenCookieHelper.createRefreshCookie(
                        rawRefreshToken,
                        isSecure
                );
                httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            }
        }
    }

    private Set<String> getTrustedOrigins() {
        Set<String> set = new HashSet<>(Set.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:8080",
                "http://127.0.0.1:8080",
                "http://localhost:3000",
                "http://127.0.0.1:3000"
        ));
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            for (String origin : allowedOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    set.add(normalizeOrigin(trimmed));
                }
            }
        }
        return set;
    }

    private String normalizeOrigin(String origin) {
        if (origin == null) return "";
        String trimmed = origin.trim().toLowerCase(Locale.ROOT);
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.startsWith("https://") && trimmed.endsWith(":443")) {
            trimmed = trimmed.substring(0, trimmed.length() - 4);
        } else if (trimmed.startsWith("http://") && trimmed.endsWith(":80")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed;
    }

    private void validateCsrfProtection(HttpServletRequest request) {
        if (request == null) return;

        // 1. Origin header check
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !origin.isBlank()) {
            String normalizedOrigin = normalizeOrigin(origin);
            if (!getTrustedOrigins().contains(normalizedOrigin)) {
                log.warn("CSRF check rejected untrusted Origin: [{}]", origin);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Untrusted request origin");
            }
        }

        // 2. Referer check when Origin is absent
        String referer = request.getHeader(HttpHeaders.REFERER);
        if ((origin == null || origin.isBlank()) && referer != null && !referer.isBlank()) {
            try {
                URI uri = URI.create(referer);
                int port = uri.getPort();
                boolean isDefaultPort = (port <= 0)
                        || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)
                        || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443);
                String refererOrigin = uri.getScheme() + "://" + uri.getHost() + (isDefaultPort ? "" : ":" + port);
                String normalizedReferer = normalizeOrigin(refererOrigin);
                if (!getTrustedOrigins().contains(normalizedReferer)) {
                    log.warn("CSRF check rejected untrusted Referer: [{}]", referer);
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Untrusted request referer");
                }
            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                log.warn("CSRF check rejected malformed Referer: [{}]", referer);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid request referer");
            }
        }

        // 3. Explicit Sec-Fetch-Site cross-site defense
        String secFetchSite = request.getHeader("Sec-Fetch-Site");
        if ("cross-site".equalsIgnoreCase(secFetchSite)) {
            if (origin == null || !getTrustedOrigins().contains(normalizeOrigin(origin))) {
                log.warn("CSRF check blocked explicit cross-site request (Sec-Fetch-Site: cross-site)");
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-site request blocked");
            }
        }

        // 4. Custom header requirement for browser-originated requests
        String requestedWith = request.getHeader("X-Requested-With");
        String flowDeskClient = request.getHeader("X-FlowDesk-Client");
        if (origin != null || referer != null) {
            if (requestedWith == null && flowDeskClient == null) {
                log.warn("CSRF check rejected browser request missing custom anti-CSRF header");
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing required CSRF protection header");
            }
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request == null) return false;
        if (request.isSecure()) return true;
        String proto = request.getHeader("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(proto);
    }
}
