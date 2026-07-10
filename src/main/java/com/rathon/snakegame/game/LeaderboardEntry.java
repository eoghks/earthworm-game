package com.rathon.snakegame.game;

/**
 * 리더보드 한 줄 — 플레이어 id·닉네임·점수(세그먼트 수).
 * playerId는 클라이언트가 동명이인과 무관하게 본인 행을 식별하는 데 쓴다.
 */
public record LeaderboardEntry(String playerId, String nickname, int score) {
}
