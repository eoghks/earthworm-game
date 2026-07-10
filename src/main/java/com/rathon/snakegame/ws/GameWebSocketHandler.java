package com.rathon.snakegame.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 게임 WebSocket 핸들러 — 수신 메시지를 명령으로 변환해 게임 루프 큐로 넘긴다.
 * 월드 상태를 직접 만지지 않으므로 스레드 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler extends TextWebSocketHandler {

    /** 닉네임 최대 길이 */
    private static final int MAX_NICKNAME_LENGTH = 16;

    private final SessionRegistry sessionRegistry;
    private final GameLoopService gameLoopService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.register(session);
        log.debug("연결 수립: 세션 수={}", sessionRegistry.allSessions().size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText("");
        switch (type) {
            case "join" -> enqueueJoin(session, root);
            case "input" -> enqueueInput(session, root);
            default -> log.debug("알 수 없는 메시지 타입 무시: {}", type);
        }
    }

    /** 입장 요청 — 닉네임을 정제해 큐에 넣는다 */
    private void enqueueJoin(WebSocketSession session, JsonNode root) {
        String nickname = sanitizeNickname(root.path("nickname").asText(""));
        gameLoopService.enqueue(new GameCommand.Join(session.getId(), nickname));
    }

    /** 조작 입력 — 목표 각도·부스트 여부를 큐에 넣는다 */
    private void enqueueInput(WebSocketSession session, JsonNode root) {
        double angle = root.path("angle").asDouble(0);
        boolean boosting = root.path("boosting").asBoolean(false);
        gameLoopService.enqueue(new GameCommand.Input(session.getId(), angle, boosting));
    }

    /** 닉네임 공백 제거·길이 제한, 비어있으면 기본값 */
    private static String sanitizeNickname(String raw) {
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return "이름없는지렁이";
        }
        return trimmed.length() > MAX_NICKNAME_LENGTH
                ? trimmed.substring(0, MAX_NICKNAME_LENGTH)
                : trimmed;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        gameLoopService.enqueue(new GameCommand.Leave(session.getId()));
        log.debug("연결 종료: status={}", status.getCode());
    }
}
