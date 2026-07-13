package com.rathon.snakegame.protocol;

import java.util.List;

import com.rathon.snakegame.game.Snake;
import com.rathon.snakegame.game.Vec2;

/**
 * 지렁이 전송용 DTO — 세그먼트는 [x, y] 배열 목록으로 직렬화해 페이로드를 줄인다.
 */
public record SnakeDto(String id, String nickname, String skinId, boolean boosting, List<double[]> segments) {

    /** 도메인 객체 → DTO 변환 */
    public static SnakeDto from(Snake snake) {
        List<double[]> segments = snake.segmentsView().stream()
                .map(SnakeDto::toPoint)
                .toList();
        return new SnakeDto(snake.getId(), snake.getNickname(), snake.getSkinId(),
                snake.isBoosting(), segments);
    }

    /** 좌표 소수 1자리 반올림 */
    private static double[] toPoint(Vec2 v) {
        return new double[] {
                Math.round(v.x() * 10) / 10.0,
                Math.round(v.y() * 10) / 10.0
        };
    }
}
