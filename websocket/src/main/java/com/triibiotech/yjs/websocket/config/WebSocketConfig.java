package com.triibiotech.yjs.websocket.config;

import com.triibiotech.yjs.websocket.handler.DocWebSocketHandler;
import com.triibiotech.yjs.websocket.handler.WebSocketInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private DocWebSocketHandler docWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(docWebSocketHandler, "/doc-collaboration/**")
                .addInterceptors(new WebSocketInterceptor())
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // 设置文本消息最大缓存（单位：字节）
        container.setMaxTextMessageBufferSize(128 * 1024);
        container.setMaxBinaryMessageBufferSize(5 * 1024 * 1024);
        // 可选：设置空闲超时（毫秒）
        container.setMaxSessionIdleTimeout(30 * 60 * 1000L);
        return container;
    }
}
