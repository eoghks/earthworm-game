package com.rathon.snakegame.ws;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket 세션과 플레이어 id 매핑 관리.
 * 세션 등록/해제는 WebSocket 스레드, 조회·바인딩은 게임 루프 스레드에서 일어나므로
 * ConcurrentHashMap으로 보호한다.
 */
@Component
public class SessionRegistry {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToPlayer = new ConcurrentHashMap<>();
    private final Map<String, String> playerToSession = new ConcurrentHashMap<>();

    /** 연결 수립 시 세션 등록 */
    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    /** 연결 종료 시 세션·플레이어 매핑 제거 */
    public void unregister(String sessionId) {
        sessions.remove(sessionId);
        Optional.ofNullable(sessionToPlayer.remove(sessionId))
                .ifPresent(playerToSession::remove);
    }

    /** 입장 승인 시 세션 ↔ 플레이어 양방향 바인딩 */
    public void bindPlayer(String sessionId, String playerId) {
        sessionToPlayer.put(sessionId, playerId);
        playerToSession.put(playerId, sessionId);
    }

    /** 사망 등으로 플레이어 바인딩만 해제 (세션은 유지 — 재입장 대기) */
    public void unbindPlayer(String playerId) {
        Optional.ofNullable(playerToSession.remove(playerId))
                .ifPresent(sessionToPlayer::remove);
    }

    public Optional<WebSocketSession> findSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public Optional<String> findPlayerBySession(String sessionId) {
        return Optional.ofNullable(sessionToPlayer.get(sessionId));
    }

    public Optional<WebSocketSession> findSessionByPlayer(String playerId) {
        return Optional.ofNullable(playerToSession.get(playerId))
                .flatMap(this::findSession);
    }

    /** 브로드캐스트 대상 전체 세션 */
    public Collection<WebSocketSession> allSessions() {
        return sessions.values();
    }
}
