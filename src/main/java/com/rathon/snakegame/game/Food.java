package com.rathon.snakegame.game;

/**
 * 맵 위의 먹이 한 개. id는 월드 내 유일하다.
 */
public record Food(long id, Vec2 position) {
}
