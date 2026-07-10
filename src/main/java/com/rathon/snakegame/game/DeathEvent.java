package com.rathon.snakegame.game;

/**
 * 지렁이 사망 이벤트 — 사망 통지·후처리에 사용한다.
 */
public record DeathEvent(String playerId, String nickname, int score) {
}
