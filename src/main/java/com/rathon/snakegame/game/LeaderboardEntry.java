package com.rathon.snakegame.game;

/**
 * 리더보드 한 줄 — 닉네임과 점수(세그먼트 수).
 */
public record LeaderboardEntry(String nickname, int score) {
}
