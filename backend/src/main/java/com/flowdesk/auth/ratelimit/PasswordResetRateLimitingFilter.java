package com.flowdesk.auth.ratelimit;

import com.flowdesk.auth.security.SecurityErrorResponseWriter;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.util.Locale;

/**
 * Spring Security filter for password reset rate limiting.
 * <p>
 * Completely isolated from login rate limiters so password reset requests can never
 * exhaust or block normal user login quotas.
 */
public class PasswordResetRateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetRateLimitingFilter.class);

    public static final String FORGOT_PASSWORD_PATH = "/api/auth/forgot-password";
    public static final String RESET_PASSWORD_PATH = "/api/auth/reset-password";

    private final ForgotPasswordRateLimiterProperties properties;
    private final SecurityErrorResponseWriter errorResponseWriter;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    private DualQuotaRateLimiter forgotPasswordRateLimiter;
    private SlidingWindowRateLimiter resetPasswordIpLimiter;

    public PasswordResetRateLimitingFilter(
            ForgotPasswordRateLimiterProperties properties,
            SecurityErrorResponseWriter errorResponseWriter,
            JsonMapper jsonMapper,
            DualQuotaRateLimiter forgotPasswordRateLimiter,
            SlidingWindowRateLimiter resetPasswordIpLimiter
    ) {
        this.properties = properties;
        this.errorResponseWriter = errorResponseWriter;
        this.jsonMapper = jsonMapper;
        this.forgotPasswordRateLimiter = forgotPasswordRateLimiter;
        this.resetPasswordIpLimiter = resetPasswordIpLimiter;
        this.clock = null;
    }

    public PasswordResetRateLimitingFilter(
            ForgotPasswordRateLimiterProperties properties,
            SecurityErrorResponseWriter errorResponseWriter,
            JsonMapper jsonMapper,
            Clock clock
    ) {
        this.properties = properties;
        this.errorResponseWriter = errorResponseWriter;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.forgotPasswordRateLimiter = null;
        this.resetPasswordIpLimiter = null;
    }

    @PostConstruct
    public void init() {
        properties.validate();
        if (this.forgotPasswordRateLimiter == null && clock != null) {
            Object lock = new Object();
            SlidingWindowRateLimiter ipLimiter = new SlidingWindowRateLimiter(
                    properties.getIpMaxAttempts(),
                    properties.getIpWindowSeconds(),
                    properties.getMaxTrackedKeys(),
                    clock,
                    lock
            );
            SlidingWindowRateLimiter ipEmailLimiter = new SlidingWindowRateLimiter(
                    properties.getIpEmailMaxAttempts(),
                    properties.getIpEmailWindowSeconds(),
                    properties.getMaxTrackedKeys(),
                    clock,
                    lock
            );
            this.forgotPasswordRateLimiter = new DualQuotaRateLimiter(ipLimiter, ipEmailLimiter, clock, lock);
            this.resetPasswordIpLimiter = new SlidingWindowRateLimiter(
                    properties.getResetPasswordIpMaxAttempts(),
                    properties.getResetPasswordIpWindowSeconds(),
                    properties.getMaxTrackedKeys(),
                    clock,
                    new Object()
            );
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        boolean isPost = HttpMethod.POST.matches(request.getMethod());
        return !isPost || (!FORGOT_PASSWORD_PATH.equals(path) && !RESET_PASSWORD_PATH.equals(path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String clientIp = ClientIpResolver.resolveClientIp(request, properties.isTrustProxyHeaders());

        if (FORGOT_PASSWORD_PATH.equals(path)) {
            handleForgotPasswordRateLimiting(request, response, filterChain, clientIp);
        } else if (RESET_PASSWORD_PATH.equals(path)) {
            handleResetPasswordRateLimiting(request, response, filterChain, clientIp);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private void handleForgotPasswordRateLimiting(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain,
            String clientIp
    ) throws IOException, ServletException {

        CachedBodyHttpServletRequest cachedRequest;
        try {
            cachedRequest = new CachedBodyHttpServletRequest(request, properties.getMaxBodyBytes());
        } catch (CachedBodyHttpServletRequest.PayloadTooLargeException e) {
            log.warn("Payload too large rejected for forgot-password attempt from IP [{}]", clientIp);
            errorResponseWriter.write(
                    request,
                    response,
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Request body exceeds maximum allowed size"
            );
            return;
        }

        String normalizedEmail = extractEmailFromBody(cachedRequest.getCachedBody());
        String ipKey = "forgot:ip:" + clientIp;
        String ipEmailKey = "forgot:ip_email:" + clientIp + ":" + (normalizedEmail != null ? normalizedEmail : "unknown");

        SlidingWindowRateLimiter.RateLimitResult result =
                forgotPasswordRateLimiter.tryAcquireDual(ipKey, ipEmailKey);

        if (!result.isAllowed()) {
            log.warn("Forgot password rate limit exceeded for client IP [{}]", clientIp);
            int retryAfter = Math.max(1, result.retryAfterSeconds());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
            errorResponseWriter.write(
                    request,
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many password reset requests. Please try again later."
            );
            return;
        }

        filterChain.doFilter(cachedRequest, response);
    }

    private void handleResetPasswordRateLimiting(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain,
            String clientIp
    ) throws IOException, ServletException {

        CachedBodyHttpServletRequest cachedRequest;
        try {
            cachedRequest = new CachedBodyHttpServletRequest(request, properties.getMaxBodyBytes());
        } catch (CachedBodyHttpServletRequest.PayloadTooLargeException e) {
            log.warn("Payload too large rejected for reset-password attempt from IP [{}]", clientIp);
            errorResponseWriter.write(
                    request,
                    response,
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Request body exceeds maximum allowed size"
            );
            return;
        }

        String ipKey = "reset:ip:" + clientIp;
        SlidingWindowRateLimiter.RateLimitResult result = resetPasswordIpLimiter.tryAcquire(ipKey);

        if (!result.isAllowed()) {
            log.warn("Reset password rate limit exceeded for client IP [{}]", clientIp);
            int retryAfter = Math.max(1, result.retryAfterSeconds());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
            errorResponseWriter.write(
                    request,
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many password reset requests. Please try again later."
            );
            return;
        }

        filterChain.doFilter(cachedRequest, response);
    }

    private String extractEmailFromBody(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JsonNode root = jsonMapper.readTree(body);
            if (root != null && root.has("email") && root.get("email").isTextual()) {
                String email = root.get("email").asText();
                if (email != null && !email.isBlank()) {
                    return email.trim().toLowerCase(Locale.ROOT);
                }
            }
        } catch (Exception ignored) {
            // Unparseable body handled gracefully downstream
        }
        return null;
    }
}
