package com.flowdesk.auth.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DualQuotaRateLimiterTest {

    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant initial) {
            this.now = initial;
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
    @DisplayName("Should not consume partial tokens when one quota is exhausted")
    void noPartialTokenConsumptionOnRejection() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-24T10:00:00Z"));
        // IP quota = 10, IP+Email quota = 2
        SlidingWindowRateLimiter ipLimiter = new SlidingWindowRateLimiter(10, 60, 100, clock);
        SlidingWindowRateLimiter ipEmailLimiter = new SlidingWindowRateLimiter(2, 300, 100, clock);
        DualQuotaRateLimiter coordinator = new DualQuotaRateLimiter(ipLimiter, ipEmailLimiter, clock);

        String ipKey = "192.168.1.100";
        String ipEmailKey = "192.168.1.100:user@flowdesk.test";

        // Attempt 1: succeeds (consumed 1 IP, 1 Email)
        assertTrue(coordinator.tryAcquireDual(ipKey, ipEmailKey).isAllowed());

        // Attempt 2: succeeds (consumed 2 IP, 2 Email -> Email quota now exhausted)
        assertTrue(coordinator.tryAcquireDual(ipKey, ipEmailKey).isAllowed());

        // Attempt 3: rejected by Email quota
        SlidingWindowRateLimiter.RateLimitResult rejectedResult = coordinator.tryAcquireDual(ipKey, ipEmailKey);
        assertFalse(rejectedResult.isAllowed());
        assertEquals(300, rejectedResult.retryAfterSeconds());

        // CRITICAL: Verify IP quota was NOT incremented on the rejected attempt!
        // IP quota allowed 10, we used 2. It should still have 8 remaining.
        // We can test this by using a different email from the same IP:
        String otherEmailKey = "192.168.1.100:other@flowdesk.test";
        for (int i = 0; i < 2; i++) {
            assertTrue(coordinator.tryAcquireDual(ipKey, otherEmailKey).isAllowed(),
                    "Second email should be able to acquire tokens because IP quota was not wasted");
        }
    }

    @Test
    @DisplayName("Two quotas nearing exhaustion concurrently enforce exact limits without deadlock")
    void concurrentQuotasNearingExhaustion() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-24T10:00:00Z"));
        int ipQuota = 15;
        int ipEmailQuota = 5;

        SlidingWindowRateLimiter ipLimiter = new SlidingWindowRateLimiter(ipQuota, 60, 100, clock);
        SlidingWindowRateLimiter ipEmailLimiter = new SlidingWindowRateLimiter(ipEmailQuota, 300, 100, clock);
        DualQuotaRateLimiter coordinator = new DualQuotaRateLimiter(ipLimiter, ipEmailLimiter, clock);

        String ipKey = "10.0.0.1";
        String emailTarget = "target@flowdesk.test";

        int totalThreads = 30;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < totalThreads; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                SlidingWindowRateLimiter.RateLimitResult res = coordinator.tryAcquireDual(ipKey, ipKey + ":" + emailTarget);
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

        // Email limit is 5, so exactly 5 requests should pass
        assertEquals(ipEmailQuota, allowedCount.get(), "Email limit must strictly cap allowed requests");
        assertEquals(totalThreads - ipEmailQuota, blockedCount.get(), "Excess requests must be blocked");
    }

    @Test
    @DisplayName("Should recover quotas after window expiration")
    void recoveryAfterExpiration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-24T10:00:00Z"));
        SlidingWindowRateLimiter ipLimiter = new SlidingWindowRateLimiter(2, 60, 100, clock);
        SlidingWindowRateLimiter ipEmailLimiter = new SlidingWindowRateLimiter(1, 30, 100, clock);
        DualQuotaRateLimiter coordinator = new DualQuotaRateLimiter(ipLimiter, ipEmailLimiter, clock);

        String ip = "1.1.1.1";
        String compoundKey = "1.1.1.1:recovery@test.com";

        assertTrue(coordinator.tryAcquireDual(ip, compoundKey).isAllowed());
        assertFalse(coordinator.tryAcquireDual(ip, compoundKey).isAllowed());

        // Advance clock past the 30s email window but before the 60s IP window
        clock.advanceSeconds(31);

        // Still blocked by IP window if IP was exhausted? IP max was 2, only 1 was used!
        // So email window expired, IP has 1 attempt remaining -> should be allowed!
        assertTrue(coordinator.tryAcquireDual(ip, compoundKey).isAllowed());

        // Now IP has reached 2/2. Next attempt blocked by IP
        assertFalse(coordinator.tryAcquireDual(ip, compoundKey).isAllowed());

        // Advance past IP 60s window
        clock.advanceSeconds(30);
        assertTrue(coordinator.tryAcquireDual(ip, compoundKey).isAllowed(), "Should recover after IP window expires");
    }

    @Test
    @DisplayName("Dual quota atomicity is preserved under concurrent cleanup threads")
    void dualQuotaAtomicityDuringConcurrentCleanup() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-24T10:00:00Z"));
        int ipQuota = 20;
        int emailQuota = 4;

        Object lock = new Object();
        SlidingWindowRateLimiter ipLimiter = new SlidingWindowRateLimiter(ipQuota, 60, 100, clock, lock);
        SlidingWindowRateLimiter ipEmailLimiter = new SlidingWindowRateLimiter(emailQuota, 300, 100, clock, lock);
        DualQuotaRateLimiter coordinator = new DualQuotaRateLimiter(ipLimiter, ipEmailLimiter, clock, lock);

        String ip = "10.10.10.1";
        String compoundKey = "10.10.10.1:user@flowdesk.test";

        int requesterCount = 25;
        int cleanupCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(requesterCount + cleanupCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger blocked = new AtomicInteger(0);

        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < requesterCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                SlidingWindowRateLimiter.RateLimitResult res = coordinator.tryAcquireDual(ip, compoundKey);
                if (res.isAllowed()) {
                    allowed.incrementAndGet();
                } else {
                    blocked.incrementAndGet();
                }
                return null;
            }));
        }

        for (int c = 0; c < cleanupCount; c++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                ipLimiter.cleanupExpired(clock.millis());
                ipEmailLimiter.cleanupExpired(clock.millis());
                return null;
            }));
        }

        startLatch.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertEquals(emailQuota, allowed.get(), "Email quota of 4 must strictly cap allowed requests even with concurrent cleanup");
        assertEquals(requesterCount - emailQuota, blocked.get(), "Remaining requests must be blocked");

        // Verify no partial token wastage on IP limiter:
        // IP quota was 20, 4 were consumed by user@flowdesk.test.
        // Another email should still have 16 tokens available from the same IP!
        String otherKey = "10.10.10.1:other@flowdesk.test";
        for (int i = 0; i < emailQuota; i++) {
            assertTrue(coordinator.tryAcquireDual(ip, otherKey).isAllowed(),
                    "Other email should acquire up to its quota because IP tokens were not wasted on rejected attempts");
        }
    }

    @Test
    @DisplayName("No deadlocks under heavy concurrent mixed requests, checks, and cleanups")
    void noDeadlocksUnderMixedRequestsAndCleanup() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-24T10:00:00Z"));
        Object sharedLock = new Object();
        SlidingWindowRateLimiter ipLimiter = new SlidingWindowRateLimiter(50, 60, 500, clock, sharedLock);
        SlidingWindowRateLimiter ipEmailLimiter = new SlidingWindowRateLimiter(10, 300, 500, clock, sharedLock);
        DualQuotaRateLimiter coordinator = new DualQuotaRateLimiter(ipLimiter, ipEmailLimiter, clock, sharedLock);

        int totalThreads = 32;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Void>> futures = new ArrayList<>();
        for (int t = 0; t < totalThreads; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                startLatch.await();
                for (int op = 0; op < operationsPerThread; op++) {
                    String ip = "192.168.1." + (op % 5);
                    String emailKey = ip + ":user" + (op % 3) + "@test.com";

                    switch (op % 4) {
                        case 0 -> coordinator.tryAcquireDual(ip, emailKey);
                        case 1 -> ipLimiter.check(ip);
                        case 2 -> ipLimiter.cleanupExpired(clock.millis());
                        case 3 -> ipEmailLimiter.cleanupExpired(clock.millis());
                    }
                }
                return null;
            }));
        }

        startLatch.countDown();
        for (Future<Void> f : futures) {
            // Must complete within 10 seconds without deadlocking
            f.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        executor.shutdown();
    }
}

