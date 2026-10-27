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

import java.io.IOException;
import java.time.Clock;
import java.util.Set;

/**
 * Spring Security filter for rate-limiting public OAuth authentication and account linking endpoints.
 */
public class OAuthRateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OAuthRateLimitingFilter.class);

    public static final String OAUTH_GOOGLE_PATH = "/api/auth/oauth2/google";
    public static final String OAUTH_LINK_PATH = "/api/auth/oauth2/google/link";

    private static final Set<String> MONITORED_PATHS = Set.of(
            OAUTH_GOOGLE_PATH,
            OAUTH_LINK_PATH
    );

    private final OAuthRateLimiterProperties properties;
    private final SecurityErrorResponseWriter errorResponseWriter;
    private final Clock clock;

    private SlidingWindowRateLimiter oauthIpLimiter;

    public OAuthRateLimitingFilter(
            OAuthRateLimiterProperties properties,
            SecurityErrorResponseWriter errorResponseWriter,
            SlidingWindowRateLimiter oauthIpLimiter
    ) {
        this.properties = properties;
        this.errorResponseWriter = errorResponseWriter;
        this.oauthIpLimiter = oauthIpLimiter;
        this.clock = null;
    }

    public OAuthRateLimitingFilter(
            OAuthRateLimiterProperties properties,
            SecurityErrorResponseWriter errorResponseWriter,
            Clock clock
    ) {
        this.properties = properties;
        this.errorResponseWriter = errorResponseWriter;
        this.clock = clock;
        this.oauthIpLimiter = null;
    }

    @PostConstruct
    public void init() {
        properties.validate();
        if (this.oauthIpLimiter == null && clock != null) {
            this.oauthIpLimiter = new SlidingWindowRateLimiter(
                    properties.getIpMaxAttempts(),
                    properties.getIpWindowSeconds(),
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
        return !isPost || !MONITORED_PATHS.contains(path);
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

        String clientIp = ClientIpResolver.resolveClientIp(request, properties.isTrustProxyHeaders());

        CachedBodyHttpServletRequest cachedRequest;
        try {
            cachedRequest = new CachedBodyHttpServletRequest(request, properties.getMaxBodyBytes());
        } catch (CachedBodyHttpServletRequest.PayloadTooLargeException e) {
            log.warn("Payload too large rejected for OAuth request from IP [{}]", clientIp);
            errorResponseWriter.write(
                    request,
                    response,
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Request body exceeds maximum allowed size"
            );
            return;
        }

        String ipKey = "oauth:ip:" + clientIp;
        SlidingWindowRateLimiter.RateLimitResult result = oauthIpLimiter.tryAcquire(ipKey);

        if (!result.isAllowed()) {
            log.warn("OAuth rate limit exceeded for client IP [{}] on path [{}]", clientIp, request.getRequestURI());
            int retryAfter = Math.max(1, result.retryAfterSeconds());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
            errorResponseWriter.write(
                    request,
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many authentication attempts. Please try again later."
            );
            return;
        }

        filterChain.doFilter(cachedRequest, response);
    }
}
