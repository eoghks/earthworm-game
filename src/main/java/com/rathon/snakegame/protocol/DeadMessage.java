package com.rathon.snakegame.protocol;

/**
 * 사망 통지 메시지 (서버→클라) — 최종 점수 포함.
 */
public record DeadMessage(String type, int score) {

    public static DeadMessage of(int score) {
        return new DeadMessage("dead", score);
    }
}
