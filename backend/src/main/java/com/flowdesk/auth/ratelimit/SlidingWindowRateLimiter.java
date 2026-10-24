package com.flowdesk.auth.ratelimit;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, strictly bounded fixed/sliding window rate limiter.
 *
 * Concurrency & Eviction Safety:
 * 1. Single Lock Coordination: All lookup, reservation, window sliding, creation, and eviction
 *    operations execute under a shared lock. No WindowCounter reference is ever leaked or held
 *    outside synchronization, eliminating detached-counter races.
 * 2. Strict Memory Bound: Capacity is enforced under lock before insertion. If the map is at
 *    maxTrackedKeys, expired entries are cleaned up under lock. If still at capacity, new keys are rejected.
 * 3. Deterministic time: uses injectable java.time.Clock.
 */
public class SlidingWindowRateLimiter {

    private final int maxAttempts;
    private final long windowMillis;
    private final int maxTrackedKeys;
    private final Clock clock;
    private final Object lock;

    private final ConcurrentHashMap<String, WindowCounter> counters;

    public SlidingWindowRateLimiter(int maxAttempts, long windowSeconds, int maxTrackedKeys, Clock clock) {
        this(maxAttempts, windowSeconds, maxTrackedKeys, clock, new Object());
    }

    public SlidingWindowRateLimiter(int maxAttempts, long windowSeconds, int maxTrackedKeys, Clock clock, Object lock) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be greater than 0");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be greater than 0");
        }
        if (maxTrackedKeys <= 0) {
            throw new IllegalArgumentException("maxTrackedKeys must be greater than 0");
        }
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowSeconds * 1000L;
        this.maxTrackedKeys = maxTrackedKeys;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.lock = Objects.requireNonNull(lock, "lock must not be null");
        this.counters = new ConcurrentHashMap<>();
    }

    public Object getLock() {
        return lock;
    }

    /**
     * Checks capacity and acquires an attempt for this key if permitted.
     * Guaranteed atomic: if blocked, no count is incremented.
     */
    public RateLimitResult tryAcquire(String key) {
        if (key == null || key.isBlank()) {
            return RateLimitResult.allowed();
        }
        synchronized (lock) {
            long now = clock.millis();
            RateLimitResult check = checkUnderLock(key, now);
            if (!check.isAllowed()) {
                return check;
            }
            return acquireUnderLock(key, now);
        }
    }

    /**
     * Checks remaining retry-after seconds without incrementing.
     */
    public RateLimitResult check(String key) {
        if (key == null || key.isBlank()) {
            return RateLimitResult.allowed();
        }
        synchronized (lock) {
            return checkUnderLock(key, clock.millis());
        }
    }

    RateLimitResult checkUnderLock(String key, long now) {
        if (key == null || key.isBlank()) {
            return RateLimitResult.allowed();
        }
        WindowCounter counter = counters.get(key);
        if (counter == null) {
            if (counters.size() >= maxTrackedKeys) {
                cleanupExpiredUnderLock(now);
                if (counters.size() >= maxTrackedKeys) {
                    return RateLimitResult.blocked((int) Math.max(1, windowMillis / 1000L));
                }
            }
            return RateLimitResult.allowed();
        }

        if (now - counter.windowStart >= windowMillis) {
            return RateLimitResult.allowed();
        }

        if (counter.count < maxAttempts) {
            return RateLimitResult.allowed();
        }

        long elapsed = now - counter.windowStart;
        long remainingMillis = Math.max(0, windowMillis - elapsed);
        int retryAfterSeconds = (int) Math.ceil(remainingMillis / 1000.0);
        return RateLimitResult.blocked(Math.max(1, retryAfterSeconds));
    }

    RateLimitResult acquireUnderLock(String key, long now) {
        if (key == null || key.isBlank()) {
            return RateLimitResult.allowed();
        }
        WindowCounter counter = counters.get(key);
        if (counter == null) {
            if (counters.size() >= maxTrackedKeys) {
                cleanupExpiredUnderLock(now);
                if (counters.size() >= maxTrackedKeys) {
                    return RateLimitResult.blocked((int) Math.max(1, windowMillis / 1000L));
                }
            }
            counter = new WindowCounter(now);
            counters.put(key, counter);
        }

        if (now - counter.windowStart >= windowMillis) {
            counter.windowStart = now;
            counter.count = 1;
            return RateLimitResult.allowed();
        }

        if (counter.count < maxAttempts) {
            counter.count++;
            return RateLimitResult.allowed();
        }

        long elapsed = now - counter.windowStart;
        long remainingMillis = Math.max(0, windowMillis - elapsed);
        int retryAfterSeconds = (int) Math.ceil(remainingMillis / 1000.0);
        return RateLimitResult.blocked(Math.max(1, retryAfterSeconds));
    }

    public long getWindowSeconds() {
        return windowMillis / 1000L;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void cleanupExpired(long now) {
        synchronized (lock) {
            cleanupExpiredUnderLock(now);
        }
    }

    private void cleanupExpiredUnderLock(long now) {
        counters.entrySet().removeIf(entry -> now - entry.getValue().windowStart >= windowMillis);
    }

    public int getTrackedKeyCount() {
        synchronized (lock) {
            return counters.size();
        }
    }

    public void reset() {
        synchronized (lock) {
            counters.clear();
        }
    }

    static class WindowCounter {
        long windowStart;
        int count;

        WindowCounter(long windowStart) {
            this.windowStart = windowStart;
            this.count = 0;
        }
    }

    public record RateLimitResult(boolean isAllowed, int retryAfterSeconds) {
        public static RateLimitResult allowed() {
            return new RateLimitResult(true, 0);
        }

        public static RateLimitResult blocked(int retryAfterSeconds) {
            return new RateLimitResult(false, retryAfterSeconds);
        }
    }
}
