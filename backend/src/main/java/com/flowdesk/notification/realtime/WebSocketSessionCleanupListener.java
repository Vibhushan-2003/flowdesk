
package com.flowdesk.notification.realtime;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketSessionCleanupListener {

    private final WebSocketSessionRegistry sessionRegistry;

    public WebSocketSessionCleanupListener(
            WebSocketSessionRegistry sessionRegistry
    ) {
        this.sessionRegistry = sessionRegistry;
    }

    /*
     * DAY 37 - PHASE 5
     *
     * Spring publishes SessionDisconnectEvent
     * when a STOMP/WebSocket session disconnects.
     *
     * Remove its authentication from the registry
     * to avoid keeping stale session records.
     */
    @EventListener
    public void handleSessionDisconnect(
            SessionDisconnectEvent event
    ) {
        sessionRegistry.remove(
                event.getSessionId()
        );
    }
}
