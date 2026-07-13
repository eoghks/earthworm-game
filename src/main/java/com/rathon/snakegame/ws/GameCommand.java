package com.rathon.snakegame.ws;

import java.util.Optional;

/**
 * WebSocket 스레드 → 게임 루프 스레드로 전달되는 명령.
 * 월드 변경은 게임 루프 스레드에서만 수행해 동기화 문제를 없앤다.
 */
public sealed interface GameCommand {

    /** 입장 요청 — 로그인 사용자면 username이 채워지고 장착 스킨이 적용된다 */
    record Join(String sessionId, String nickname, String skinId,
                Optional<String> username) implements GameCommand {
    }

    /** 조작 입력 — 목표 각도와 부스트 여부 */
    record Input(String sessionId, double angle, boolean boosting) implements GameCommand {
    }

    /** 연결 종료 */
    record Leave(String sessionId) implements GameCommand {
    }
}
