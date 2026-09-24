package com.flowdesk.sla.scheduler;

import com.flowdesk.sla.service.SlaMonitoringService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
