package com.rathon.snakegame.ws;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rathon.snakegame.member.MemberService;

/**
 * GameWebSocketHandler 단위 테스트 — 신뢰 경계 입력 방어(비유한 각도·파싱 불가 페이로드) 검증.
 */
class GameWebSocketHandlerTest {

    private final GameLoopService gameLoopService = mock(GameLoopService.class);
    private final MemberService memberService = mock(MemberService.class);
    private final WebSocketSession session = mock(WebSocketSession.class);
    private GameWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        when(session.getId()).thenReturn("s1");
        handler = new GameWebSocketHandler(new SessionRegistry(), gameLoopService,
                new ObjectMapper(), memberService);
    }

    @Test
    @DisplayName("입력 방어: 문자열 NaN 각도는 명령 큐에 적재되지 않는다")
    void handleTextMessage_dropsNanAngle() {
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"input\",\"angle\":\"NaN\"}"));

        verify(gameLoopService, never()).enqueue(any());
    }

    @Test
    @DisplayName("입력 방어: Infinity 각도는 명령 큐에 적재되지 않는다")
    void handleTextMessage_dropsInfinityAngle() {
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"input\",\"angle\":\"Infinity\",\"boosting\":true}"));

        verify(gameLoopService, never()).enqueue(any());
    }

    @Test
    @DisplayName("정상 입력: 유한 각도는 Input 명령으로 적재된다")
    void handleTextMessage_enqueuesFiniteAngle() {
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"input\",\"angle\":1.5,\"boosting\":true}"));

        verify(gameLoopService).enqueue(new GameCommand.Input("s1", 1.5, true));
    }

    @Test
    @DisplayName("파싱 방어: JSON이 아닌 페이로드는 예외 없이 무시된다")
    void handleTextMessage_ignoresMalformedJson() {
        assertThatCode(() -> handler.handleTextMessage(session, new TextMessage("not-json{{")))
                .doesNotThrowAnyException();

        verify(gameLoopService, never()).enqueue(any());
    }
}
