
package com.flowdesk.notification.realtime;

import org.junit.jupiter.api.Test;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSocketOutboundMessageHeadersTest {

    @Test
    void sessionSpecificMessageCarriesSessionId() {

        SimpMessageHeaderAccessor accessor =
                SimpMessageHeaderAccessor.create();

        accessor.setSessionId("session-123");
        accessor.setDestination(
                "/queue/notifications-user123"
        );

        Message<byte[]> message =
                MessageBuilder.createMessage(
                        new byte[0],
                        accessor.getMessageHeaders()
                );

        assertEquals(
                "session-123",
                SimpMessageHeaderAccessor
                        .getSessionId(message.getHeaders())
        );
    }
}
