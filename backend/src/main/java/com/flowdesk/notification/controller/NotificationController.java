package com.flowdesk.notification.controller;

import com.flowdesk.common.dto.PageResponse;
import com.flowdesk.notification.dto.NotificationResponse;
import com.flowdesk.notification.dto.UnreadNotificationCountResponse;
import com.flowdesk.notification.service.NotificationService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(
            NotificationService notificationService
    ) {
        this.notificationService =
                notificationService;
    }

    @GetMapping
    public ResponseEntity<
            PageResponse<NotificationResponse>
            > getMyNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(
                    defaultValue = "0"
            ) int page,
            @RequestParam(
                    defaultValue = "20"
            ) int size
    ) {
        UUID userId =
                getAuthenticatedUserId(jwt);

        return ResponseEntity.ok(
                notificationService
                        .getMyNotifications(
                                userId,
                                page,
                                size
                        )
        );
    }

    @GetMapping("/unread-count")
    public ResponseEntity<
            UnreadNotificationCountResponse
            > getUnreadCount(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId =
                getAuthenticatedUserId(jwt);

        return ResponseEntity.ok(
                notificationService
                        .getUnreadCount(userId)
        );
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse>
    markAsRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID notificationId
    ) {
        UUID userId =
                getAuthenticatedUserId(jwt);

        return ResponseEntity.ok(
                notificationService
                        .markAsRead(
                                userId,
                                notificationId
                        )
        );
    }

    private UUID getAuthenticatedUserId(
            Jwt jwt
    ) {
        return UUID.fromString(
                jwt.getSubject()
        );
    }
}