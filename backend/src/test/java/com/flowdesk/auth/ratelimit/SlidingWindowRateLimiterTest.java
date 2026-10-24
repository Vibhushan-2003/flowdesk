package com.flowdesk.auth.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowRateLimiterTest {

    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant initial) {
            this.now = initial;
        }

        void setTime(Instant newTime) {
            this.now = newTime;
        }

        void advanceSeconds(long seconds) {
            this.now = this.now.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    @DisplayName("Should allow requests within quota and block on exceeding limit")
    void allowWithinQuotaAndBlockExceeding() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-24T10:00:00Z"));
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(3, 60, 100, clock);

        // 3 requests allowed
        assertTrue(limiter.tryAcquire("test-key").isAllowed());
        assertTrue(limiter.tryAcquire("test-key").isAllowed());
        assertTrue(limiter.tryAcquire("test-key").isAllowed());

        // 4th request blocked with positive retry-after
        SlidingWindowRateLimiter.RateLimitResult result = limiter.tryAcquire("test-key");
        assertFalse(result.isAllowed());
        assertEquals(60, result.retryAfterSeconds());
    }

    @Test
    @DisplayName("Should restore quota after window expiry")
    void restoreAfterWindowExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-24T10:00:00Z"));
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(2, 60, 100, clock);

        assertTrue(limiter.tryAcquire("test-key").isAllowed());
        assertTrue(limiter.tryAcquire("test-key").isAllowed());
        assertFalse(limiter.tryAcquire("test-key").isAllowed());

        // Advance clock by 30 seconds (still blocked)
        clock.advanceSeconds(30);
        SlidingWindowRateLimiter.RateLimitResult midResult = limiter.tryAcquire("test-key");
        assertFalse(midResult.isAllowed());
        assertEquals(30, midResult.retryAfterSeconds());

        // Advance past window (61 seconds from start)
        clock.advanceSeconds(31);
        assertTrue(limiter.tryAcquire("test-key").isAllowed(), "Access should be restored after window expiration");
    }

    @Test
    @DisplayName("Should isolate quotas between different keys")
    void isolateDifferentKeys() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-24T10:00:00Z"));
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, 60, 100, clock);

        assertTrue(limiter.tryAcquire("key-a").isAllowed());
        assertFalse(limiter.tryAcquire("key-a").isAllowed());

        // key-b has separate quota
        assertTrue(limiter.tryAcquire("key-b").isAllowed());
        assertFalse(limiter.tryAcquire("key-b").isAllowed());
    }

    @Test
    @DisplayName("Should enforce bounded memory under adversarial key cardinality")
    void boundedMemoryCap() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-24T10:00:00Z"));
        int maxKeys = 10;
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(5, 60, maxKeys, clock);

        for (int i = 0; i < maxKeys; i++) {
            assertTrue(limiter.tryAcquire("ip-" + i).isAllowed());
        }
        assertEquals(maxKeys, limiter.getTrackedKeyCount());

        // Attempting to track 11th key when full and unexpired returns blocked
        SlidingWindowRateLimiter.RateLimitResult result = limiter.tryAcquire("ip-overflow");
        assertFalse(result.isAllowed());

        // Advance clock past expiration
        clock.advanceSeconds(61);
        limiter.cleanupExpired(clock.millis());
        assertEquals(0, limiter.getTrackedKeyCount());

        // Now new key is allowed
        assertTrue(limiter.tryAcquire("ip-new").isAllowed());
    }

    @Test
    @DisplayName("Concurrent requests cannot exceed configured quota")
    void concurrentRequestsThreadSafety() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-24T10:00:00Z"));
        int quota = 10;
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(quota, 60, 100, clock);

        int totalThreads = 30;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < totalThreads; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                SlidingWindowRateLimiter.RateLimitResult res = limiter.tryAcquire("shared-ip");
                if (res.isAllowed()) {
                    allowedCount.incrementAndGet();
                } else {
                    blockedCount.incrementAndGet();
                }
                return null;
            }));
        }

        startLatch.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertEquals(quota, allowedCount.get(), "Exactly configured quota of requests must be allowed");
        assertEquals(totalThreads - quota, blockedCount.get(), "Excess concurrent requests must be blocked");
    }

    @Test
    @DisplayName("Strict tracked-key cap enforced under concurrent allocation of new keys")
    void strictTrackedKeyCapUnderConcurrency() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-24T10:00:00Z"));
        int maxTrackedKeys = 20;
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(5, 60, maxTrackedKeys, clock);

        int totalThreads = 25;
        int keysPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Void>> futures = new ArrayList<>();
        for (int t = 0; t < totalThreads; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                startLatch.await();
                for (int k = 0; k < keysPerThread; k++) {
                    limiter.tryAcquire("concurrent-key-" + threadId + "-" + k);
                }
                return null;
            }));
        }

        startLatch.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertTrue(limiter.getTrackedKeyCount() <= maxTrackedKeys,
                "Tracked key count must NEVER exceed maxTrackedKeys (" + maxTrackedKeys + "), but was " + limiter.getTrackedKeyCount());
    }

    @Test
    @DisplayName("In-flight counter lookup racing with cleanup must never allow detached-counter quota bypass")
    void inFlightLookupRacingWithCleanup() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-24T10:00:00Z"));
        int quota = 5;
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(quota, 60, 100, clock);

        int workerThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(workerThreads + 2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);

        List<Future<Void>> futures = new ArrayList<>();

        // Workers attempting to acquire for the shared key
        for (int i = 0; i < workerThreads; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                SlidingWindowRateLimiter.RateLimitResult res = limiter.tryAcquire("shared-key");
                if (res.isAllowed()) {
                    allowedCount.incrementAndGet();
                } else {
                    blockedCount.incrementAndGet();
                }
                return null;
            }));
        }

        // Concurrent cleanup threads racing simultaneously
        for (int c = 0; c < 2; c++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                limiter.cleanupExpired(clock.millis());
                return null;
            }));
        }

        startLatch.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertEquals(quota, allowedCount.get(), "Quota must be strictly capped at 5 even under concurrent cleanup");
        assertEquals(workerThreads - quota, blockedCount.get(), "Excess requests must be blocked");
    }

    @Test
    @DisplayName("Expired counter eviction racing with replacement resets window correctly without detached counter")
    void expiredEvictionRacingWithReplacement() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-24T10:00:00Z"));
        int quota = 3;
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(quota, 60, 100, clock);

        // Exhaust quota in first window
        for (int i = 0; i < quota; i++) {
            assertTrue(limiter.tryAcquire("racing-key").isAllowed());
        }
        assertFalse(limiter.tryAcquire("racing-key").isAllowed());

        // Advance clock past expiration
        clock.advanceSeconds(65);

        // Race cleanup and acquires simultaneously
        int racers = 6;
        ExecutorService executor = Executors.newFixedThreadPool(racers);
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(racers);
        AtomicInteger newWindowAllowed = new AtomicInteger(0);

        List<Future<Void>> futures = new ArrayList<>();
        // 2 cleanup threads
        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                limiter.cleanupExpired(clock.millis());
                return null;
            }));
        }
        // 4 acquire threads (quota is 3, so exactly 3 must be allowed in the new window)
        for (int i = 0; i < 4; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                if (limiter.tryAcquire("racing-key").isAllowed()) {
                    newWindowAllowed.incrementAndGet();
                }
                return null;
            }));
        }

        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertEquals(quota, newWindowAllowed.get(), "Exactly 3 requests must be allowed in new window despite concurrent cleanup");
        // Next acquire must be blocked
        assertFalse(limiter.tryAcquire("racing-key").isAllowed(), "Subsequent acquire must be blocked once quota is reached");
    }

    @Test
    @DisplayName("Multiple requests at the exact expiry boundary transition cleanly without desynchronization")
    void multipleRequestsAtExpiryBoundary() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-24T10:00:00Z"));
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(2, 60, 100, clock);

        assertTrue(limiter.tryAcquire("boundary-key").isAllowed());
        assertTrue(limiter.tryAcquire("boundary-key").isAllowed());
        assertFalse(limiter.tryAcquire("boundary-key").isAllowed());

        // At 59 seconds: still blocked (1 second remaining)
        clock.advanceSeconds(59);
        SlidingWindowRateLimiter.RateLimitResult res59 = limiter.check("boundary-key");
        assertFalse(res59.isAllowed());
        assertEquals(1, res59.retryAfterSeconds());

        // At exactly 60 seconds (boundary reached): allowed
        clock.advanceSeconds(1);
        assertTrue(limiter.check("boundary-key").isAllowed());
        assertTrue(limiter.tryAcquire("boundary-key").isAllowed()); // 1st attempt in new window
        assertTrue(limiter.tryAcquire("boundary-key").isAllowed()); // 2nd attempt in new window
        assertFalse(limiter.tryAcquire("boundary-key").isAllowed()); // 3rd attempt blocked
    }
}


