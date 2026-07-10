package com.rathon.snakegame.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 순수 WebSocket 엔드포인트 설정 — 게임 틱 브로드캐스트에 STOMP 계층은 불필요하다.
 * 허용 오리진은 프로퍼티로 외부화해 Cross-Site WebSocket Hijacking을 차단한다.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;
    private final String[] allowedOrigins;

    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler,
                           @Value("${game.allowed-origins:http://localhost:8085}") String[] allowedOrigins) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/ws")
                .setAllowedOrigins(allowedOrigins);
    }
}
