package com.rathon.snakegame.ws;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rathon.snakegame.game.GameConfig;
import com.rathon.snakegame.member.Member;
import com.rathon.snakegame.member.MemberService;

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
    private final MemberService memberService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.register(session);
        log.debug("연결 수립: 세션 수={}", sessionRegistry.allSessions().size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        parse(message.getPayload()).ifPresent(root -> dispatch(session, root));
    }

    /** 신뢰 경계 입력 방어 — 파싱 불가 페이로드는 세션을 끊지 않고 무시한다 */
    private Optional<JsonNode> parse(String payload) {
        try {
            return Optional.of(objectMapper.readTree(payload));
        } catch (JsonProcessingException e) {
            log.debug("JSON 파싱 실패 — 메시지 무시");
            return Optional.empty();
        }
    }

    /** 메시지 타입별 분기 */
    private void dispatch(WebSocketSession session, JsonNode root) {
        String type = root.path("type").asText("");
        switch (type) {
            case "join" -> enqueueJoin(session, root);
            case "input" -> enqueueInput(session, root);
            default -> log.debug("알 수 없는 메시지 타입 무시: {}", type);
        }
    }

    /** 입장 요청 — 로그인 사용자는 회원 닉네임을 기본값으로 쓰고 장착 스킨을 적용한다 */
    private void enqueueJoin(WebSocketSession session, JsonNode root) {
        // 핸드셰이크 시점 인증 사용자 조회 — WebSocket 스레드라 DB 접근 가능 (게임 루프 스레드 아님)
        Optional<Member> member = resolveMember(session);
        String requested = root.path("nickname").asText("");
        String nickname = sanitizeNickname(requested.isBlank()
                ? member.map(Member::getNickname).orElse("")
                : requested);
        String skinId = member.map(Member::getEquippedSkinId).orElse(GameConfig.DEFAULT_SKIN_ID);
        gameLoopService.enqueue(new GameCommand.Join(
                session.getId(), nickname, skinId, member.map(Member::getUsername)));
    }

    /**
     * 핸드셰이크에서 전달된 인증 주체로 회원 조회 — 게스트는 empty.
     * Principal은 핸드셰이크 시점에 고정되므로 다른 탭에서 로그아웃해도 열린 연결의 join은
     * 기존 계정으로 적립·스킨 적용된다 — 본인 계정이라 피해 주체가 없어 정책상 허용한다.
     */
    private Optional<Member> resolveMember(WebSocketSession session) {
        return Optional.ofNullable(session.getPrincipal())
                .flatMap(principal -> memberService.findByUsername(principal.getName()));
    }

    /** 조작 입력 — 목표 각도·부스트 여부를 큐에 넣는다. 비유한 각도는 좌표 오염을 막기 위해 폐기 */
    private void enqueueInput(WebSocketSession session, JsonNode root) {
        double angle = root.path("angle").asDouble(0);
        if (!Double.isFinite(angle)) {
            log.debug("비유한 각도 입력 폐기: session={}", session.getId());
            return;
        }
        boolean boosting = root.path("boosting").asBoolean(false);
        gameLoopService.enqueue(new GameCommand.Input(session.getId(), angle, boosting));
    }

    /** 닉네임 정제 — 제어·서식 문자 제거, 공백 정리, 코드포인트 기준 길이 제한, 비어있으면 기본값 */
    private static String sanitizeNickname(String raw) {
        // zero-width·RTL override·개행 등 표기 스푸핑에 쓰이는 문자를 걸러낸다
        String cleaned = raw.replaceAll("[\\p{Cc}\\p{Cf}]", "").strip();
        if (cleaned.isEmpty()) {
            return "이름없는지렁이";
        }
        return truncateByCodePoints(cleaned, MAX_NICKNAME_LENGTH);
    }

    /** 서로게이트 쌍(이모지)이 중간에서 잘리지 않도록 코드포인트 기준으로 절단한다 */
    private static String truncateByCodePoints(String value, int maxCodePoints) {
        if (value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        gameLoopService.enqueue(new GameCommand.Leave(session.getId()));
        log.debug("연결 종료: status={}", status.getCode());
    }
}
