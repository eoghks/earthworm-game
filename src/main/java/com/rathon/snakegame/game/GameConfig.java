package com.rathon.snakegame.game;

/**
 * 게임 전역 상수 정의.
 * 맵 크기·틱레이트·이동/부스트/충돌 관련 수치를 한곳에서 관리한다.
 */
public final class GameConfig {

    private GameConfig() {
        // 상수 홀더 — 인스턴스 생성 금지
    }

    /** 기본 맵 반지름 (중심 원점) — 기준 인원 이하일 때의 크기 */
    public static final double BASE_RADIUS = 2000.0;

    /** 기본 반지름이 유지되는 기준 인원 — 초과 시 √비례로 목표 반지름이 커진다 */
    public static final int BASE_PLAYER_COUNT = 5;

    /** 맵 확장 속도 (픽셀/틱) — 목표 반지름까지 빠르게 넓힌다 */
    public static final double MAP_EXPAND_PER_TICK = 8.0;

    /** 맵 수축 속도 (픽셀/틱) — 느리게 조여 경계 밖 플레이어가 대응할 시간을 준다 */
    public static final double MAP_SHRINK_PER_TICK = 2.0;

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

    /** 기본 반지름 기준 먹이 목표 개수 — 실제 목표량은 맵 면적에 비례해 조정된다 */
    public static final int FOOD_COUNT = 400;

    /** 먹이 생성 위치가 맵 경계에서 확보할 여유 거리 */
    public static final double FOOD_SPAWN_MARGIN = 50.0;

    /** 먹이 섭취 판정 거리 (머리 중심 ~ 먹이 중심) */
    public static final double FOOD_EAT_DISTANCE = 22.0;

    /** 먹이 1개 섭취 시 늘어나는 세그먼트 수 */
    public static final int GROWTH_PER_FOOD = 1;

    /**
     * 기본 몸 반지름 — 초기 길이(INITIAL_LENGTH)일 때의 굵기.
     * 클라이언트 기존 렌더값(SEGMENT_RADIUS=10)과 맞춰 회귀를 최소화한다.
     */
    public static final double BASE_SNAKE_RADIUS = 10.0;

    /** 몸 반지름 상한 — 아무리 길어도 이 굵기를 넘지 않는다 */
    public static final double MAX_SNAKE_RADIUS = 34.0;

    /**
     * 반지름 성장 계수 — radius = BASE + K·√(length - INITIAL_LENGTH).
     * K=2.0이면 델타 24(=MAX-BASE)에 도달하는 지점이 √(len-10)=12 → 길이 154 부근으로,
     * 초반엔 눈에 띄게 굵어지다 상한에 완만히 수렴한다.
     */
    public static final double SNAKE_GROWTH_K = 2.0;

    /** 사망 시 몸이 먹이로 변환되는 간격 — N세그먼트당 먹이 1개 */
    public static final int CORPSE_FOOD_INTERVAL = 2;

    /** 리더보드 표시 인원 */
    public static final int LEADERBOARD_SIZE = 10;

    /** 스폰 위치가 맵 경계에서 확보할 여유 거리 */
    public static final double SPAWN_MARGIN = 300.0;

    /** 크레딧 환산비 — 최종 점수 N점당 크레딧 1 (내림) */
    public static final int CREDIT_SCORE_DIVISOR = 10;

    /** 기본 스킨 id — 게스트·미장착 회원에게 적용 (카탈로그 정의는 skin 패키지) */
    public static final String DEFAULT_SKIN_ID = "default";
}
