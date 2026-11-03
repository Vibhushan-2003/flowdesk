package com.flowdesk.notification.realtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConfigOriginTest {

    @Test
    @DisplayName("WebSocketConfig splits comma-separated origins and passes them as discrete array elements")
    void splitsCommaSeparatedAllowedOrigins() {
        WebSocketJwtChannelInterceptor inInterceptor = mock(WebSocketJwtChannelInterceptor.class);
        WebSocketOutboundSecurityInterceptor outInterceptor = mock(WebSocketOutboundSecurityInterceptor.class);

        String originsInput = "https://flowdesk.onrender.com, http://localhost:5173, https://app.flowdesk.demo";
        WebSocketConfig config = new WebSocketConfig(inInterceptor, outInterceptor, originsInput);

        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration endpointRegistration = mock(StompWebSocketEndpointRegistration.class);

        when(registry.addEndpoint("/ws")).thenReturn(endpointRegistration);

        config.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws");

        ArgumentCaptor<String[]> originsCaptor = ArgumentCaptor.forClass(String[].class);
        verify(endpointRegistration).setAllowedOrigins(originsCaptor.capture());

        String[] capturedOrigins = originsCaptor.getValue();
        assertThat(capturedOrigins).containsExactly(
                "https://flowdesk.onrender.com",
                "http://localhost:5173",
                "https://app.flowdesk.demo"
        );
    }
}

