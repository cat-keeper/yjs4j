package com.triibiotech.yjs.websocket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Yjs WebSocket Server - Spring Boot Application
 *
 * @author zbs
 * @date 2025/07/31  23:47:07
 */
@SpringBootApplication
public class YjsWebSocketServer {

    public static void main(String[] args) {
        System.out.println("Starting Yjs WebSocket Server...");
        SpringApplication.run(YjsWebSocketServer.class, args);
    }
}
