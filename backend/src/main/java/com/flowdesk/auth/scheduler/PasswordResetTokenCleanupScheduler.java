package com.flowdesk.auth.scheduler;

import com.flowdesk.auth.service.PasswordResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Periodically cleans up expired and consumed password reset tokens older than the retention threshold.
 * <p>
 * Ensures active, unexpired tokens are never deleted.
 */
@Component
public class PasswordResetTokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenCleanupScheduler.class);

    private final PasswordResetService passwordResetService;

    public PasswordResetTokenCleanupScheduler(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /*
     * fixedDelay means the next cleanup run starts only after the previous run finishes.
     * Defaults:
     * - wait 60 seconds after startup
     * - then run every hour (3600000 ms)
     */
    @Scheduled(
            fixedDelayString = "${app.security.password-reset.cleanup.fixed-delay-ms:3600000}",
            initialDelayString = "${app.security.password-reset.cleanup.initial-delay-ms:60000}"
    )
    public void cleanupTokens() {
        try {
            int deleted = passwordResetService.cleanupOldTokens(Duration.ofDays(7));
            if (deleted > 0) {
                log.info("Password reset token cleanup purged {} expired/consumed token(s).", deleted);
            }
        } catch (RuntimeException ex) {
            log.error("Scheduled password reset token cleanup failed", ex);
        }
    }
}
