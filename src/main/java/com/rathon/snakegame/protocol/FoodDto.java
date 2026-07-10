package com.rathon.snakegame.protocol;

import com.rathon.snakegame.game.Food;

/**
 * 먹이 전송용 DTO.
 */
public record FoodDto(long id, double x, double y) {

    /** 도메인 객체 → DTO 변환 (좌표 소수 1자리 반올림으로 페이로드 절감) */
    public static FoodDto from(Food food) {
        return new FoodDto(
                food.id(),
                Math.round(food.position().x() * 10) / 10.0,
                Math.round(food.position().y() * 10) / 10.0);
    }
}
