package com.flowdesk.auth.ratelimit;

import java.time.Clock;
import java.util.Objects;

/**
 * Coordinates atomic checking and consumption across both IP and IP+Email quotas.
 *
 * Concurrency & Eviction Safety:
 * 1. Consistent Global Lock Ordering: Locks ipLimiter's lock first, then ipEmailLimiter's lock
 *    (or a single shared coordinator lock). This guarantees strict deadlock freedom.
 * 2. Elimination of Detached Counters: Counter lookups and mutations are executed entirely
 *    within the synchronized block by key. No intermediate counter references are held or
 *    returned across lock boundaries, ensuring concurrent cleanup can NEVER evict a counter
 *    while an in-flight reservation is using it.
 * 3. Atomic Dual-Quota Reservation: Both quotas are verified for capacity before either is consumed.
 *    If either fails, neither counter is incremented, ensuring zero quota leakage on blocked attempts.
 */
public class DualQuotaRateLimiter {

    private final SlidingWindowRateLimiter ipLimiter;
    private final SlidingWindowRateLimiter ipEmailLimiter;
    private final Clock clock;
    private final Object lock;

    public DualQuotaRateLimiter(
            SlidingWindowRateLimiter ipLimiter,
            SlidingWindowRateLimiter ipEmailLimiter,
            Clock clock
    ) {
        this(ipLimiter, ipEmailLimiter, clock, null);
    }

    public DualQuotaRateLimiter(
            SlidingWindowRateLimiter ipLimiter,
            SlidingWindowRateLimiter ipEmailLimiter,
            Clock clock,
            Object lock
    ) {
        this.ipLimiter = Objects.requireNonNull(ipLimiter, "ipLimiter must not be null");
        this.ipEmailLimiter = Objects.requireNonNull(ipEmailLimiter, "ipEmailLimiter must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.lock = lock;
    }

    /**
     * Atomically tries to acquire a token from both the IP quota and the IP+Email quota.
     *
     * @param ipKey the client IP
     * @param ipEmailKey the compound key (IP:normalized_email)
     * @return RateLimitResult - allowed if both quotas have capacity; blocked with max retry-after otherwise
     */
    public SlidingWindowRateLimiter.RateLimitResult tryAcquireDual(String ipKey, String ipEmailKey) {
        Object firstLock = (lock != null) ? lock : ipLimiter.getLock();
        Object secondLock = (lock != null) ? lock : ipEmailLimiter.getLock();

        // Lock order: firstLock (IP limiter) then secondLock (IP+Email limiter).
        // If firstLock == secondLock (shared coordinator lock), second synchronized is an instant re-entrant no-op.
        synchronized (firstLock) {
            synchronized (secondLock) {
                long now = clock.millis();

                // Step 1: Pre-flight check both quotas under lock by key
                SlidingWindowRateLimiter.RateLimitResult ipCheck = ipLimiter.checkUnderLock(ipKey, now);
                SlidingWindowRateLimiter.RateLimitResult ipEmailCheck = ipEmailLimiter.checkUnderLock(ipEmailKey, now);

                if (!ipCheck.isAllowed() || !ipEmailCheck.isAllowed()) {
                    int retryAfter = Math.max(ipCheck.retryAfterSeconds(), ipEmailCheck.retryAfterSeconds());
                    return SlidingWindowRateLimiter.RateLimitResult.blocked(Math.max(1, retryAfter));
                }

                // Step 2: Both quotas have capacity! Atomically consume both together under the same lock.
                ipLimiter.acquireUnderLock(ipKey, now);
                ipEmailLimiter.acquireUnderLock(ipEmailKey, now);

                return SlidingWindowRateLimiter.RateLimitResult.allowed();
            }
        }
    }

    public SlidingWindowRateLimiter getIpLimiter() {
        return ipLimiter;
    }

    public SlidingWindowRateLimiter getIpEmailLimiter() {
        return ipEmailLimiter;
    }

    public void reset() {
        Object firstLock = (lock != null) ? lock : ipLimiter.getLock();
        Object secondLock = (lock != null) ? lock : ipEmailLimiter.getLock();
        synchronized (firstLock) {
            synchronized (secondLock) {
                ipLimiter.reset();
                ipEmailLimiter.reset();
            }
        }
    }
}
