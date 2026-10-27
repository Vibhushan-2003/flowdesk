package com.flowdesk.user.scheduler;

import com.flowdesk.user.service.UserInvitationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class UserInvitationCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserInvitationCleanupScheduler.class);

    private final UserInvitationService userInvitationService;

    public UserInvitationCleanupScheduler(UserInvitationService userInvitationService) {
        this.userInvitationService = userInvitationService;
    }

    @Scheduled(
            fixedDelayString = "${app.security.invitation.cleanup.fixed-delay-ms:3600000}",
            initialDelayString = "${app.security.invitation.cleanup.initial-delay-ms:120000}"
    )
    public void cleanupInvitations() {
        try {
            int deleted = userInvitationService.cleanupOldInvitations(Duration.ofDays(30));
            if (deleted > 0) {
                log.info("User invitation cleanup purged {} expired/consumed invitation(s).", deleted);
            }
        } catch (Exception ex) {
            log.error("Scheduled user invitation cleanup failed", ex);
        }
    }
}
