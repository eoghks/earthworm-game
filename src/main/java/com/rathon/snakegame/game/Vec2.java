package com.rathon.snakegame.game;

/**
 * 불변 2차원 벡터. 위치·방향 계산에 사용한다.
 */
public record Vec2(double x, double y) {

    /** 영벡터 */
    public static final Vec2 ZERO = new Vec2(0, 0);

    /** 각도(라디안)로부터 단위 벡터 생성 */
    public static Vec2 fromAngle(double angle) {
        return new Vec2(Math.cos(angle), Math.sin(angle));
    }

    /** 벡터 덧셈 */
    public Vec2 plus(Vec2 other) {
        return new Vec2(x + other.x, y + other.y);
    }

    /** 벡터 뺄셈 (this - other) */
    public Vec2 minus(Vec2 other) {
        return new Vec2(x - other.x, y - other.y);
    }

    /** 스칼라 곱 */
    public Vec2 scale(double factor) {
        return new Vec2(x * factor, y * factor);
    }

    /** 벡터 크기 */
    public double length() {
        return Math.hypot(x, y);
    }

    /** 두 점 사이 거리 */
    public double distanceTo(Vec2 other) {
        return minus(other).length();
    }
}
