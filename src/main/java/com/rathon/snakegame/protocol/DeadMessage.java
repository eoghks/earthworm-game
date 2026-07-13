package com.rathon.snakegame.protocol;

/**
 * 사망 통지 메시지 (서버→클라) — 최종 점수와 이번 라운드 적립 크레딧 포함.
 * creditEarned는 게스트면 항상 0이다.
 */
public record DeadMessage(String type, int score, long creditEarned) {

    public static DeadMessage of(int score, long creditEarned) {
        return new DeadMessage("dead", score, creditEarned);
    }
}
