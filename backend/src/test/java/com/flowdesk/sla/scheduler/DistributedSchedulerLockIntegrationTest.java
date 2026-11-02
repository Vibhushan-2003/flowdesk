package com.flowdesk.sla.scheduler;

import com.flowdesk.sla.domain.SlaEventType;
import com.flowdesk.sla.repository.SlaEventRepository;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketType;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DistributedSchedulerLockIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SlaEventRepository slaEventRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private LockProvider instance1LockProvider;
    private LockProvider instance2LockProvider;

    @BeforeEach
    void setUp() {
        // Clean up previous locks for tests
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM shedlock WHERE name LIKE 'test_%' OR name = 'SlaMonitoringScheduler_monitorSlaBreaches'");

        // Two independent LockProvider instances simulating two distributed application nodes
        instance1LockProvider = new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );

        instance2LockProvider = new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }

    @Test
    @DisplayName("Exclusive execution: only one instance obtains exclusive lock among concurrent invocations")
    void testConcurrentSchedulerLockMutualExclusion() throws Exception {
        String lockName = "test_concurrent_sla_lock";
        LockConfiguration lockConfig = new LockConfiguration(
                Instant.now(),
                lockName,
                Duration.ofMinutes(2),
                Duration.ofSeconds(5)
        );

        int concurrentWorkers = 2;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentWorkers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentWorkers);

        List<Optional<SimpleLock>> acquiredLocks = Collections.synchronizedList(new ArrayList<>());

        // Node 1 attempts to lock
        executor.submit(() -> {
            try {
                startLatch.await();
                Optional<SimpleLock> lock = instance1LockProvider.lock(lockConfig);
                acquiredLocks.add(lock);
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        // Node 2 attempts to lock simultaneously
        executor.submit(() -> {
            try {
                startLatch.await();
                Optional<SimpleLock> lock = instance2LockProvider.lock(lockConfig);
                acquiredLocks.add(lock);
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // Exactly one node must have acquired the lock
        long successfulAcquisitions = acquiredLocks.stream().filter(Optional::isPresent).count();
        assertThat(successfulAcquisitions).isEqualTo(1);

        // Unlock the winner
        acquiredLocks.stream()
                .filter(Optional::isPresent)
                .findFirst()
                .get()
                .get()
                .unlock();
    }

    @Test
    @DisplayName("Crash safety: expired lock is automatically reclaimed by another instance without deadlock")
    void testLockExpirationReclaimAfterSimulatedCrash() throws Exception {
        String lockName = "test_crash_recovery_lock";
        // Node 1 acquires lock with short lockAtMostFor (2 seconds) and crashes (never calls unlock)
        LockConfiguration crashLockConfig = new LockConfiguration(
                Instant.now(),
                lockName,
                Duration.ofSeconds(2),
                Duration.ZERO
        );

        Optional<SimpleLock> node1Lock = instance1LockProvider.lock(crashLockConfig);
        assertThat(node1Lock).isPresent();

        // Immediate attempt by Node 2 must fail while lock is active
        LockConfiguration node2Attempt = new LockConfiguration(
                Instant.now(),
                lockName,
                Duration.ofMinutes(1),
                Duration.ZERO
        );
        Optional<SimpleLock> node2Immediate = instance2LockProvider.lock(node2Attempt);
        assertThat(node2Immediate).isEmpty();

        // Wait for lockAtMostFor to elapse (2.2 seconds) simulating Node 1 crash
        Thread.sleep(2200);

        // Node 2 should now successfully acquire the lock
        Optional<SimpleLock> node2AfterCrash = instance2LockProvider.lock(node2Attempt);
        assertThat(node2AfterCrash).isPresent();

        node2AfterCrash.get().unlock();
    }

    @Test
    @DisplayName("SLA database idempotency: concurrent breach event insertion prevents duplicate audit/events")
    void testConcurrentSlaBreachEventInsertionIdempotency() throws Exception {
        User user = new User("sched." + UUID.randomUUID() + "@flowdesk.test", "hash", "Sched", "Test");
        User savedUser = userRepository.save(user);

        Ticket ticket = new Ticket(
                "SCHED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                TicketType.INCIDENT,
                "Concurrent SLA Test",
                "Testing concurrent breach recording",
                savedUser
        );
        Ticket savedTicket = ticketRepository.save(ticket);
        OffsetDateTime deadline = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successfulInserts = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    transactionTemplate.execute(status -> {
                        int inserted = slaEventRepository.insertIfAbsent(
                                UUID.randomUUID(),
                                savedTicket.getId(),
                                SlaEventType.RESPONSE_BREACHED.name(),
                                deadline
                        );
                        if (inserted == 1) {
                            successfulInserts.incrementAndGet();
                        }
                        return inserted;
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // Exactly one concurrent thread must succeed in inserting the breach event
        assertThat(successfulInserts.get()).isEqualTo(1);
    }
}
