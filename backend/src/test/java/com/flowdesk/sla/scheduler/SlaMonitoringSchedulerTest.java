package com.flowdesk.sla.scheduler;

import com.flowdesk.sla.service.SlaMonitoringService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaMonitoringSchedulerTest {

    @Mock
    private SlaMonitoringService
            slaMonitoringService;

    @Test
    void schedulerDelegatesToMonitoringService() {
        SlaMonitoringScheduler scheduler =
                new SlaMonitoringScheduler(
                        slaMonitoringService
                );

        when(
                slaMonitoringService
                        .monitorBreaches()
        ).thenReturn(2);

        scheduler.monitorSlaBreaches();

        verify(
                slaMonitoringService
        ).monitorBreaches();
    }

    @Test
    void schedulerKeepsRunningWhenMonitoringFails() {
        SlaMonitoringScheduler scheduler =
                new SlaMonitoringScheduler(
                        slaMonitoringService
                );

        when(
                slaMonitoringService
                        .monitorBreaches()
        ).thenThrow(
                new RuntimeException(
                        "Database temporarily unavailable"
                )
        );

        assertDoesNotThrow(
                scheduler::monitorSlaBreaches
        );

        verify(
                slaMonitoringService
        ).monitorBreaches();
    }
}
