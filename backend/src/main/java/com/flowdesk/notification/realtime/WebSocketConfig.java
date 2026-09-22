package com.flowdesk.notification.realtime;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.context.annotation.Configuration;

import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;

import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    private final WebSocketJwtChannelInterceptor
            webSocketJwtChannelInterceptor;

    private final String allowedOrigin;

    public WebSocketConfig(
            WebSocketJwtChannelInterceptor
                    webSocketJwtChannelInterceptor,

            @Value("${app.cors.allowed-origin}")
            String allowedOrigin
    ) {
        this.webSocketJwtChannelInterceptor =
                webSocketJwtChannelInterceptor;

        this.allowedOrigin =
                allowedOrigin;
    }

    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry
    ) {

        registry
                .addEndpoint("/ws")
                .setAllowedOrigins(
                        allowedOrigin
                );
    }

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry
    ) {

        /*
         * Client SEND destinations would begin
         * with /app.
         *
         * We do not need client SEND messages
         * for notifications yet, but defining
         * this now keeps the architecture clear.
         */
        registry.setApplicationDestinationPrefixes(
                "/app"
        );

        /*
         * Simple in-memory broker.
         *
         * Notification messages will ultimately
         * be delivered to:
         *
         * /user/queue/notifications
         */
        registry.enableSimpleBroker(
                "/queue"
        );

        registry.setUserDestinationPrefix(
                "/user"
        );
    }

    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration
    ) {

        registration.interceptors(
                webSocketJwtChannelInterceptor
        );
    }
}