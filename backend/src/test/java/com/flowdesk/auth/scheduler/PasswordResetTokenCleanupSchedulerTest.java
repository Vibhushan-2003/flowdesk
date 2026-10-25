package com.flowdesk.auth.scheduler;

import com.flowdesk.auth.service.PasswordResetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenCleanupSchedulerTest {

    @Mock
    private PasswordResetService passwordResetService;

    @InjectMocks
    private PasswordResetTokenCleanupScheduler scheduler;

    @Test
    @DisplayName("Scheduled cleanup invokes cleanupOldTokens on PasswordResetService")
    void cleanupTokensInvokesService() {
        when(passwordResetService.cleanupOldTokens(any(Duration.class))).thenReturn(5);

        scheduler.cleanupTokens();

        verify(passwordResetService, times(1)).cleanupOldTokens(Duration.ofDays(7));
    }

    @Test
    @DisplayName("Scheduled cleanup handles service runtime exception without throwing")
    void cleanupTokensHandlesExceptionGracefully() {
        doThrow(new RuntimeException("Database timeout")).when(passwordResetService).cleanupOldTokens(any());

        assertDoesNotThrow(() -> scheduler.cleanupTokens());
    }
}
