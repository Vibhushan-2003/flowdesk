
package com.flowdesk.notification.realtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class WebSocketSessionCleanupListenerTest {

    @Mock
    private WebSocketSessionRegistry sessionRegistry;

    @Test
    void disconnectMustRemoveRegisteredSession() {

        String sessionId = "session-123";

        SimpMessageHeaderAccessor accessor =
                SimpMessageHeaderAccessor.create(
                        SimpMessageType.DISCONNECT
                );

        accessor.setSessionId(sessionId);

        var message = MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );

        SessionDisconnectEvent event =
                new SessionDisconnectEvent(
                        this,
                        message,
                        sessionId,
                        CloseStatus.NORMAL
                );

        WebSocketSessionCleanupListener listener =
                new WebSocketSessionCleanupListener(
                        sessionRegistry
                );

        listener.handleSessionDisconnect(event);

        verify(sessionRegistry).remove(sessionId);
    }

@Test
void repeatedDisconnectMustBeSafe() {

    String sessionId = "session-repeat";

    SimpMessageHeaderAccessor accessor =
            SimpMessageHeaderAccessor.create(
                    SimpMessageType.DISCONNECT
            );

    accessor.setSessionId(sessionId);

    var message = MessageBuilder.createMessage(
            new byte[0],
            accessor.getMessageHeaders()
    );

    SessionDisconnectEvent event =
            new SessionDisconnectEvent(
                    this,
                    message,
                    sessionId,
                    CloseStatus.NORMAL
            );

    WebSocketSessionCleanupListener listener =
            new WebSocketSessionCleanupListener(
                    sessionRegistry
            );

    listener.handleSessionDisconnect(event);
    listener.handleSessionDisconnect(event);

    verify(sessionRegistry, times(2)).remove(sessionId);
}

}
