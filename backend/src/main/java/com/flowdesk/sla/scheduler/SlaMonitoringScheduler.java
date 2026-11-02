package com.flowdesk.sla.scheduler;

import com.flowdesk.sla.service.SlaMonitoringService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically evaluates SLA response and resolution deadlines for open tickets.
 * <p>
 * Uses PostgreSQL distributed locking (@SchedulerLock) to guarantee single-instance
 * execution in multi-instance deployments while remaining fully functional on single instances.
 */
@Component
public class SlaMonitoringScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    SlaMonitoringScheduler.class
            );

    private final SlaMonitoringService
            slaMonitoringService;

    public SlaMonitoringScheduler(
            SlaMonitoringService slaMonitoringService
    ) {
        this.slaMonitoringService =
                slaMonitoringService;
    }

    /*
     * fixedDelay means the next run starts only after
     * the previous run finishes.
     *
     * The defaults are intentionally conservative:
     * - wait 60 seconds after application startup
     * - then run every 60 seconds
     *
     * Both values can be overridden with application
     * properties without changing code.
     */
    @Scheduled(
            fixedDelayString =
                    "${flowdesk.sla.monitoring.fixed-delay-ms:60000}",
            initialDelayString =
                    "${flowdesk.sla.monitoring.initial-delay-ms:60000}"
    )
    @SchedulerLock(
            name = "SlaMonitoringScheduler_monitorSlaBreaches",
            lockAtMostFor = "${flowdesk.sla.monitoring.lock-at-most-for:5m}",
            lockAtLeastFor = "${flowdesk.sla.monitoring.lock-at-least-for:30s}"
    )
    public void monitorSlaBreaches() {
        try {
            int createdEvents =
                    slaMonitoringService
                            .monitorBreaches();

            if (createdEvents > 0) {
                LOGGER.info(
                        "Recorded {} new SLA breach event(s)",
                        createdEvents
                );
            }
        }
        catch (RuntimeException exception) {
            /*
             * One failed monitoring cycle must not stop
             * future scheduled executions.
             *
             * The service transaction rolls back the
             * failed cycle; the next scheduler run can
             * retry safely because event insertion is
             * idempotent.
             */
            LOGGER.error(
                    "SLA breach monitoring run failed",
                    exception
            );
        }
    }
}
