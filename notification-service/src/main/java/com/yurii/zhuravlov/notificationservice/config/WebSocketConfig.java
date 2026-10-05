package com.yurii.zhuravlov.notificationservice.config;

import com.yurii.zhuravlov.notificationservice.config.properties.WebSocketProperties;
import com.yurii.zhuravlov.notificationservice.ws.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler handler;
    private final WebSocketProperties properties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, properties.path())
                // The browser client will be served from a different origin than this
                // service. Tighten this to the real origins before anything public.
                .setAllowedOriginPatterns("*");
    }
}