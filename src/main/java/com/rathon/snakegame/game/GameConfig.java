package com.rathon.snakegame.game;

/**
 * 게임 전역 상수 정의.
 * 맵 크기·틱레이트·이동/부스트/충돌 관련 수치를 한곳에서 관리한다.
 */
public final class GameConfig {

    private GameConfig() {
        // 상수 홀더 — 인스턴스 생성 금지
    }

    /** 원형 맵 반지름 (중심 원점) */
    public static final double MAP_RADIUS = 2000.0;

    /** 초당 틱 수 */
    public static final int TICK_RATE = 20;

    /** 틱 간격 (밀리초) */
    public static final long TICK_INTERVAL_MS = 1000L / TICK_RATE;

    /** 기본 이동 속도 (픽셀/틱) */
    public static final double BASE_SPEED = 6.0;

    /** 부스트 이동 속도 (픽셀/틱) */
    public static final double BOOST_SPEED = 12.0;

    /** 틱당 최대 회전량 (라디안) */
    public static final double MAX_TURN_RATE = 0.3;

    /** 몸 세그먼트 간격 (픽셀) */
    public static final double SEGMENT_SPACING = 10.0;

    /** 입장 시 초기 세그먼트 수 */
    public static final int INITIAL_LENGTH = 10;

    /** 부스트 가능 최소 세그먼트 수 — 이 길이 이하로는 부스트 불가 */
    public static final int MIN_BOOST_LENGTH = INITIAL_LENGTH;

    /** 부스트 중 세그먼트 1개가 소모되기까지 걸리는 틱 수 */
    public static final int BOOST_DRAIN_TICKS = 5;

    /** 맵에 유지할 먹이 개수 */
    public static final int FOOD_COUNT = 400;

    /** 먹이 섭취 판정 거리 (머리 중심 ~ 먹이 중심) */
    public static final double FOOD_EAT_DISTANCE = 22.0;

    /** 먹이 1개 섭취 시 늘어나는 세그먼트 수 */
    public static final int GROWTH_PER_FOOD = 1;

    /** 머리-몸 충돌 판정 거리 */
    public static final double COLLISION_DISTANCE = 14.0;

    /** 사망 시 몸이 먹이로 변환되는 간격 — N세그먼트당 먹이 1개 */
    public static final int CORPSE_FOOD_INTERVAL = 2;

    /** 리더보드 표시 인원 */
    public static final int LEADERBOARD_SIZE = 10;

    /** 스폰 위치가 맵 경계에서 확보할 여유 거리 */
    public static final double SPAWN_MARGIN = 300.0;
}
