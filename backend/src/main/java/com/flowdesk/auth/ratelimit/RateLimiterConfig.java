package com.flowdesk.auth.ratelimit;

import com.flowdesk.auth.security.SecurityErrorResponseWriter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

@Configuration
public class RateLimiterConfig {

    @Bean
    public Clock rateLimiterClock() {
        return Clock.systemUTC();
    }

    @Bean
    public DualQuotaRateLimiter dualQuotaRateLimiter(
            RateLimiterProperties properties,
            Clock rateLimiterClock
    ) {
        Object rateLimiterLock = new Object();
        SlidingWindowRateLimiter ipLimiter = new SlidingWindowRateLimiter(
                properties.getIpMaxAttempts(),
                properties.getIpWindowSeconds(),
                properties.getMaxTrackedKeys(),
                rateLimiterClock,
                rateLimiterLock
        );
        SlidingWindowRateLimiter ipEmailLimiter = new SlidingWindowRateLimiter(
                properties.getIpEmailMaxAttempts(),
                properties.getIpEmailWindowSeconds(),
                properties.getMaxTrackedKeys(),
                rateLimiterClock,
                rateLimiterLock
        );
        return new DualQuotaRateLimiter(ipLimiter, ipEmailLimiter, rateLimiterClock, rateLimiterLock);
    }

    @Bean
    public LoginRateLimitingFilter loginRateLimitingFilter(
            RateLimiterProperties properties,
            SecurityErrorResponseWriter securityErrorResponseWriter,
            JsonMapper jsonMapper,
            DualQuotaRateLimiter dualQuotaRateLimiter
    ) {
        return new LoginRateLimitingFilter(
                properties,
                securityErrorResponseWriter,
                jsonMapper,
                dualQuotaRateLimiter
        );
    }

    /**
     * Disable automatic servlet container registration for LoginRateLimitingFilter.
     * This guarantees the filter is only invoked via the Spring Security filter chain,
     * preventing accidental duplicate execution.
     */
    @Bean
    public FilterRegistrationBean<LoginRateLimitingFilter> loginRateLimitingFilterRegistration(
            LoginRateLimitingFilter filter
    ) {
        FilterRegistrationBean<LoginRateLimitingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
