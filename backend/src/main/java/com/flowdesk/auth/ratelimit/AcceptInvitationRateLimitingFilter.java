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

/**
 * Spring Security filter for rate-limiting public invitation acceptance requests.
 * <p>
 * Completely isolated from login and password reset rate limiters.
 */
public class AcceptInvitationRateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AcceptInvitationRateLimitingFilter.class);
    public static final String ACCEPT_INVITATION_PATH = "/api/auth/accept-invitation";

    private final InvitationRateLimiterProperties properties;
    private final SecurityErrorResponseWriter errorResponseWriter;
    private final Clock clock;

    private SlidingWindowRateLimiter acceptIpLimiter;

    public AcceptInvitationRateLimitingFilter(
            InvitationRateLimiterProperties properties,
            SecurityErrorResponseWriter errorResponseWriter,
            SlidingWindowRateLimiter acceptIpLimiter
    ) {
        this.properties = properties;
        this.errorResponseWriter = errorResponseWriter;
        this.acceptIpLimiter = acceptIpLimiter;
        this.clock = null;
    }

    public AcceptInvitationRateLimitingFilter(
            InvitationRateLimiterProperties properties,
            SecurityErrorResponseWriter errorResponseWriter,
            Clock clock
    ) {
        this.properties = properties;
        this.errorResponseWriter = errorResponseWriter;
        this.clock = clock;
        this.acceptIpLimiter = null;
    }

    @PostConstruct
    public void init() {
        properties.validate();
        if (this.acceptIpLimiter == null && clock != null) {
            this.acceptIpLimiter = new SlidingWindowRateLimiter(
                    properties.getAcceptIpMaxAttempts(),
                    properties.getAcceptIpWindowSeconds(),
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
        return !isPost || !ACCEPT_INVITATION_PATH.equals(path);
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
            log.warn("Payload too large rejected for accept-invitation attempt from IP [{}]", clientIp);
            errorResponseWriter.write(
                    request,
                    response,
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Request body exceeds maximum allowed size"
            );
            return;
        }

        String ipKey = "accept:ip:" + clientIp;
        SlidingWindowRateLimiter.RateLimitResult result = acceptIpLimiter.tryAcquire(ipKey);

        if (!result.isAllowed()) {
            log.warn("Accept invitation rate limit exceeded for client IP [{}]", clientIp);
            int retryAfter = Math.max(1, result.retryAfterSeconds());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
            errorResponseWriter.write(
                    request,
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many invitation acceptance attempts. Please try again later."
            );
            return;
        }

        filterChain.doFilter(cachedRequest, response);
    }
}
