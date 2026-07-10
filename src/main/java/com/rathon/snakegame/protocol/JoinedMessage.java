package com.rathon.snakegame.protocol;

import java.util.List;

/**
 * 입장 승인 메시지 (서버→클라) — 플레이어 id, 맵 정보, 현재 먹이 전체 스냅샷.
 */
public record JoinedMessage(String type, String playerId, double mapRadius, List<FoodDto> foods) {

    public static JoinedMessage of(String playerId, double mapRadius, List<FoodDto> foods) {
        return new JoinedMessage("joined", playerId, mapRadius, foods);
    }
}
