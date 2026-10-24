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
 * Spring Security filter for login rate limiting.
 *
 * Requirements & Security Guarantees:
 * 1. Only intercepts POST /api/auth/login. All other endpoints pass through immediately.
 * 2. Does not interfere with CORS preflight requests (OPTIONS).
 * 3. Enforces atomic dual quotas via DualQuotaRateLimiter:
 *    - Per-client-IP quota (default 30 per 60s)
 *    - Per-(client-IP + normalized-email) quota (default 5 per 300s)
 * 4. Checks and consumes quotas atomically with consistent lock ordering. If either quota
 *    is exceeded, returns HTTP 429 with Retry-After header, standard FlowDesk ApiError JSON,
 *    and Cache-Control: no-store, without wasting quota on blocked attempts.
 * 5. Strictly protects against body memory exhaustion: rejects payloads exceeding maxBodyBytes
 *    with HTTP 413 before expensive JSON parsing.
 * 6. Preserves downstream request body readability for normal login attempts via CachedBodyHttpServletRequest.
 * 7. Safe logging: removes parser exception details, request body contents, emails, and passwords from logs.
 * 8. Relies strictly on socket remote address from ClientIpResolver to eliminate header spoofing.
 */
public class LoginRateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitingFilter.class);

    public static final String LOGIN_PATH = "/api/auth/login";

    private final RateLimiterProperties properties;
    private final SecurityErrorResponseWriter errorResponseWriter;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    private DualQuotaRateLimiter dualQuotaRateLimiter;

    public LoginRateLimitingFilter(
            RateLimiterProperties properties,
            SecurityErrorResponseWriter errorResponseWriter,
            JsonMapper jsonMapper,
            DualQuotaRateLimiter dualQuotaRateLimiter
    ) {
        this.properties = properties;
        this.errorResponseWriter = errorResponseWriter;
        this.jsonMapper = jsonMapper;
        this.dualQuotaRateLimiter = dualQuotaRateLimiter;
        this.clock = null;
    }

    public LoginRateLimitingFilter(
            RateLimiterProperties properties,
            SecurityErrorResponseWriter errorResponseWriter,
            JsonMapper jsonMapper,
            Clock clock
    ) {
        this.properties = properties;
        this.errorResponseWriter = errorResponseWriter;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.dualQuotaRateLimiter = null;
    }

    @PostConstruct
    public void init() {
        properties.validate();
        if (this.dualQuotaRateLimiter == null && clock != null) {
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
            this.dualQuotaRateLimiter = new DualQuotaRateLimiter(ipLimiter, ipEmailLimiter, clock, lock);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only filter POST /api/auth/login
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !LOGIN_PATH.equals(path);
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

        CachedBodyHttpServletRequest wrappedRequest;
        try {
            wrappedRequest = new CachedBodyHttpServletRequest(request, properties.getMaxBodyBytes());
        } catch (CachedBodyHttpServletRequest.PayloadTooLargeException e) {
            log.debug("Login request payload exceeded maximum allowed size of {} bytes", properties.getMaxBodyBytes());
            errorResponseWriter.write(
                    request,
                    response,
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Request payload exceeds maximum allowed size."
            );
            return;
        }

        String clientIp = ClientIpResolver.resolveClientIp(wrappedRequest);
        String normalizedEmail = extractNormalizedEmail(wrappedRequest);

        String ipKey = clientIp;
        String ipEmailKey = clientIp + ":" + (normalizedEmail != null ? normalizedEmail : "unknown");

        // Atomic dual-quota reservation:
        // Evaluates capacity for both quotas simultaneously under consistent lock order.
        // Increments both together, or neither if either quota lacks capacity.
        SlidingWindowRateLimiter.RateLimitResult result = dualQuotaRateLimiter.tryAcquireDual(ipKey, ipEmailKey);

        if (!result.isAllowed()) {
            blockRequest(wrappedRequest, response, result.retryAfterSeconds());
            return;
        }

        // Quotas satisfied. Pass wrapped request downstream so AuthController can read body.
        filterChain.doFilter(wrappedRequest, response);
    }

    private void blockRequest(
            HttpServletRequest request,
            HttpServletResponse response,
            int retryAfterSeconds
    ) throws IOException {
        int retryAfter = Math.max(1, retryAfterSeconds);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");

        errorResponseWriter.write(
                request,
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many login attempts. Please try again later."
        );
    }

    private String extractNormalizedEmail(CachedBodyHttpServletRequest request) {
        try {
            byte[] body = request.getCachedBody();
            if (body == null || body.length == 0) {
                return null;
            }
            JsonNode root = jsonMapper.readTree(body);
            if (root != null && root.has("email")) {
                String email = root.get("email").asText(null);
                if (email != null && !email.isBlank()) {
                    return email.trim().toLowerCase(Locale.ROOT);
                }
            }
        } catch (Exception e) {
            // Generic diagnostic message without raw body or exception string
            log.debug("Failed to extract normalized email from login request body");
        }
        return null;
    }

    // Accessors for testing and monitoring
    public SlidingWindowRateLimiter getIpLimiter() {
        return dualQuotaRateLimiter != null ? dualQuotaRateLimiter.getIpLimiter() : null;
    }

    public SlidingWindowRateLimiter getIpEmailLimiter() {
        return dualQuotaRateLimiter != null ? dualQuotaRateLimiter.getIpEmailLimiter() : null;
    }

    public DualQuotaRateLimiter getDualQuotaRateLimiter() {
        return dualQuotaRateLimiter;
    }
}
