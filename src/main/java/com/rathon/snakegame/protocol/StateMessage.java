package com.rathon.snakegame.protocol;

import java.util.List;

import com.rathon.snakegame.game.LeaderboardEntry;

/**
 * 매 틱 상태 브로드캐스트 (서버→클라).
 * 먹이는 전체 목록 대신 증분(added/removed)으로 보내 페이로드를 줄인다 —
 * 전체 스냅샷은 입장 시 JoinedMessage로 1회 전달된다.
 */
public record StateMessage(
        String type,
        double mapRadius,
        List<SnakeDto> snakes,
        List<FoodDto> foodsAdded,
        List<Long> foodsRemoved,
        List<LeaderboardEntry> leaderboard) {

    public static StateMessage of(double mapRadius, List<SnakeDto> snakes, List<FoodDto> foodsAdded,
                                  List<Long> foodsRemoved, List<LeaderboardEntry> leaderboard) {
        return new StateMessage("state", mapRadius, snakes, foodsAdded, foodsRemoved, leaderboard);
    }
}
