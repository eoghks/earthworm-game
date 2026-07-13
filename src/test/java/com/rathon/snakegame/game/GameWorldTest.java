package com.rathon.snakegame.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * GameWorld 단위 테스트 — 섭취·충돌·경계·사망 변환·리더보드 규칙 검증.
 * 고정 시드 Random으로 결정적으로 동작한다.
 */
class GameWorldTest {

    private GameWorld world;

    @BeforeEach
    void setUp() {
        // 자동 리스폰 먹이의 간섭을 없애기 위해 먹이 목표량 0인 월드로 검증한다
        world = new GameWorld(new Random(42), 0);
    }

    @Test
    @DisplayName("먹이 섭취: 머리 근처 먹이가 제거되고 지렁이가 성장한다")
    void tick_consumesFoodAndGrows() {
        Snake snake = world.spawnSnakeAt("p1", "먹보", new Vec2(500, 500), 0);
        Food food = world.spawnFoodAt(new Vec2(500 + GameConfig.BASE_SPEED, 500));
        int initialScore = snake.score();

        world.tick(); // 이동 후 머리가 먹이 위치에 도달 → 섭취

        assertThat(world.foods().stream().map(Food::id)).doesNotContain(food.id());
        assertThat(world.getRemovedFoodIds()).containsExactly(food.id());

        world.tick(); // 예약된 성장 반영
        assertThat(snake.score()).isEqualTo(initialScore + GameConfig.GROWTH_PER_FOOD);
    }

    @Test
    @DisplayName("충돌 사망: 내 머리가 다른 지렁이 몸에 닿으면 죽는다")
    void tick_killsSnakeOnBodyCollision() {
        world.spawnSnakeAt("victim", "피해자", new Vec2(0, 0), 0);
        // 공격자 머리가 한 틱 이동 후 피해자 몸 세그먼트(-50, 0) 위에 오도록 배치
        world.spawnSnakeAt("attacker", "가해자", new Vec2(-50, -GameConfig.BASE_SPEED), Math.PI / 2);

        List<DeathEvent> deaths = world.tick();

        assertThat(deaths).extracting(DeathEvent::playerId).containsExactly("attacker");
        assertThat(world.findSnake("attacker")).isEmpty();
        assertThat(world.findSnake("victim")).isPresent();
    }

    @Test
    @DisplayName("충돌 반지름: 굵은 지렁이 몸에는 얇은 지렁이-지렁이 판정 거리보다 먼 거리에서도 충돌한다")
    void tick_thickSnakeCollisionReachesBeyondBaseThreshold() {
        // 얇은 지렁이를 일직선으로 길러 굵게 만든다 — 직선 정상상태에서 몸은 매 틱 (+BASE_SPEED, 0)만 이동
        Snake thick = world.spawnSnakeAt("thick", "굵은놈", new Vec2(-1000, 0), 0);
        thick.grow(90);
        for (int i = 0; i < 100; i++) {
            world.tick(); // 성장 반영 후 길이 100 — 이후 틱은 순수 전진
        }
        double thickRadius = thick.radius();
        double thinRadius = GameConfig.BASE_SNAKE_RADIUS;
        double gap = thinRadius * 2 + 5; // 얇은-얇은 임계(=반지름 합)보다 크지만 굵은 몸에는 닿는 거리
        // 경계값 전제: 얇은끼리라면 안 닿고(gap > 2·BASE), 굵은 몸에는 닿는다(gap ≤ 굵은+얇은)
        assertThat(gap).isGreaterThan(thinRadius * 2);
        assertThat(gap).isLessThanOrEqualTo(thickRadius + thinRadius);

        // 다음 틱 후 굵은 몸의 k번째 세그먼트 위치를 예측해 그 위 gap 지점으로 얇은 공격자 머리를 보낸다
        double headX = thick.head().x();
        double targetX = headX + GameConfig.BASE_SPEED - 10 * 10; // k=10번째 세그먼트
        world.spawnSnakeAt("thin", "얇은놈", new Vec2(targetX, gap + GameConfig.BASE_SPEED), -Math.PI / 2);

        List<DeathEvent> deaths = world.tick();

        assertThat(deaths).extracting(DeathEvent::playerId).containsExactly("thin");
        assertThat(world.findSnake("thick")).isPresent();
    }

    @Test
    @DisplayName("경계 사망: 머리가 맵 반지름을 벗어나면 죽는다")
    void tick_killsSnakeOutOfBounds() {
        world.spawnSnakeAt("p1", "탈주자", new Vec2(GameConfig.BASE_RADIUS - 1, 0), 0);

        List<DeathEvent> deaths = world.tick();

        assertThat(deaths).extracting(DeathEvent::playerId).containsExactly("p1");
        assertThat(world.findSnake("p1")).isEmpty();
    }

    @Test
    @DisplayName("사망 변환: 죽은 지렁이 몸이 먹이로 배출된다")
    void tick_convertsDeadBodyToFood() {
        Snake snake = world.spawnSnakeAt("p1", "탈주자", new Vec2(GameConfig.BASE_RADIUS - 1, 0), 0);
        int bodySize = snake.score();
        int expectedFoods = (bodySize + GameConfig.CORPSE_FOOD_INTERVAL - 1) / GameConfig.CORPSE_FOOD_INTERVAL;

        world.tick();

        assertThat(world.getAddedFoods()).hasSize(expectedFoods);
    }

    @Test
    @DisplayName("부스트 배출: 부스트로 소모된 길이가 꼬리 위치 먹이로 나온다")
    void tick_dropsFoodWhileBoosting() {
        Snake snake = world.spawnSnakeAt("p1", "질주자", new Vec2(0, 0), 0);
        snake.grow(5);
        for (int i = 0; i < 5; i++) {
            world.tick(); // 성장 반영 — 길이 15
        }
        int lengthBefore = snake.score();
        world.applyInput("p1", 0, true);

        for (int i = 0; i < GameConfig.BOOST_DRAIN_TICKS; i++) {
            world.tick();
        }

        assertThat(snake.score()).isEqualTo(lengthBefore - 1);
        // 배출된 먹이는 떼어낸 꼬리 위치에 생긴다
        assertThat(world.getAddedFoods()).hasSize(1);
    }

    @Test
    @DisplayName("리더보드: 길이 내림차순으로 정렬된다")
    void leaderboard_sortsByScoreDescending() {
        world.spawnSnakeAt("p1", "일등", new Vec2(0, 1000), 0).grow(10);
        world.spawnSnakeAt("p2", "이등", new Vec2(0, -1000), 0).grow(5);
        world.spawnSnakeAt("p3", "삼등", new Vec2(-1000, 0), 0);
        for (int i = 0; i < 10; i++) {
            world.tick(); // 성장 반영
        }

        List<LeaderboardEntry> board = world.leaderboard();

        assertThat(board).extracting(LeaderboardEntry::nickname)
                .startsWith("일등", "이등", "삼등");
        assertThat(board).isSortedAccordingTo(
                (a, b) -> Integer.compare(b.score(), a.score()));
    }

    @Test
    @DisplayName("리더보드: 최대 인원까지만 노출된다")
    void leaderboard_limitsToConfiguredSize() {
        for (int i = 0; i < GameConfig.LEADERBOARD_SIZE + 3; i++) {
            world.spawnSnakeAt("p" + i, "선수" + i, new Vec2(i * 100.0, 0), 0);
        }

        assertThat(world.leaderboard()).hasSize(GameConfig.LEADERBOARD_SIZE);
    }

    @Test
    @DisplayName("먹이 리스폰: 틱 후에도 맵 전체 먹이 수가 목표량 이상으로 유지된다")
    void tick_keepsFoodCountReplenished() {
        GameWorld fullWorld = new GameWorld(new Random(42));
        fullWorld.spawnSnakeAt("p1", "먹보", new Vec2(0, 0), 0);

        fullWorld.tick();

        assertThat(fullWorld.foods().size()).isGreaterThanOrEqualTo(GameConfig.FOOD_COUNT);
    }

    @Test
    @DisplayName("강제 사망 처리: killSnake는 몸을 먹이로 배출하며 제거한다")
    void killSnake_dropsCorpseFood() {
        Snake snake = world.spawnSnakeAt("p1", "재입장자", new Vec2(0, 0), 0);
        int expectedFoods = (snake.score() + GameConfig.CORPSE_FOOD_INTERVAL - 1)
                / GameConfig.CORPSE_FOOD_INTERVAL;

        world.killSnake("p1");

        assertThat(world.findSnake("p1")).isEmpty();
        assertThat(world.getAddedFoods()).hasSize(expectedFoods);
    }

    @Test
    @DisplayName("재입장 회귀: killSnake 후 tick을 돌려도 시체 먹이가 증분 기록에 남는다")
    void tick_afterKillSnake_keepsCorpseFoodInDeltas() {
        Snake snake = world.spawnSnakeAt("p1", "재입장자", new Vec2(0, 0), 0);
        int expectedFoods = (snake.score() + GameConfig.CORPSE_FOOD_INTERVAL - 1)
                / GameConfig.CORPSE_FOOD_INTERVAL;

        // 게임 루프 순서 재현: 틱 시작 → 명령 처리(killSnake) → 월드 진행 → 브로드캐스트
        world.beginTick();
        world.killSnake("p1");
        world.tick();

        assertThat(world.getAddedFoods()).hasSize(expectedFoods);
    }

    @Test
    @DisplayName("틱 시작: beginTick은 이전 틱의 먹이 증분 기록을 비운다")
    void beginTick_clearsPreviousDeltas() {
        world.spawnFoodAt(new Vec2(100, 100));
        assertThat(world.getAddedFoods()).isNotEmpty();

        world.beginTick();

        assertThat(world.getAddedFoods()).isEmpty();
        assertThat(world.getRemovedFoodIds()).isEmpty();
    }

    @Test
    @DisplayName("경계 밖 먹이: 수축 여부와 무관하게 틱 말미에 제거된다")
    void tick_removesOutOfBoundsFoodEvenWithoutShrink() {
        Food outside = world.spawnFoodAt(new Vec2(GameConfig.BASE_RADIUS + 10, 0));

        world.beginTick();
        world.tick();

        assertThat(world.foods().stream().map(Food::id)).doesNotContain(outside.id());
        assertThat(world.getRemovedFoodIds()).contains(outside.id());
    }

    @Test
    @DisplayName("목표 반지름: 기준 인원까지는 기본 크기, 초과 시 √비례로 커진다")
    void targetRadius_scalesWithSqrtOfPlayerCount() {
        assertThat(world.targetRadius()).isEqualTo(GameConfig.BASE_RADIUS); // 0명

        spawnSnakesInLine(GameConfig.BASE_PLAYER_COUNT); // 5명
        assertThat(world.targetRadius()).isEqualTo(GameConfig.BASE_RADIUS);

        spawnSnakesInLine(20); // 20명 → sqrt(20/5) = 2배
        assertThat(world.targetRadius()).isCloseTo(GameConfig.BASE_RADIUS * 2, within(1e-9));
    }

    @Test
    @DisplayName("맵 확장: 목표보다 작으면 틱당 확장 한도만큼만 커진다")
    void tick_expandsRadiusAtLimitedRate() {
        spawnSnakesInLine(20); // 목표 반지름 = 기본의 2배

        world.tick();

        assertThat(world.mapRadius())
                .isCloseTo(GameConfig.BASE_RADIUS + GameConfig.MAP_EXPAND_PER_TICK, within(1e-9));
    }

    @Test
    @DisplayName("맵 수축: 인원이 줄면 틱당 수축 한도만큼만 느리게 조여든다")
    void tick_shrinksSlowlyWhenPlayersLeave() {
        spawnSnakesInLine(20);
        for (int i = 0; i < 5; i++) {
            world.tick(); // 5틱 확장 — 반지름 = 기본 + 40
        }
        double expanded = world.mapRadius();
        removeSnakesInLine(20); // 전원 퇴장 → 목표 반지름이 기본으로 복귀

        world.tick();

        assertThat(world.mapRadius())
                .isCloseTo(expanded - GameConfig.MAP_SHRINK_PER_TICK, within(1e-9));
    }

    @Test
    @DisplayName("맵 수축: 경계 밖으로 밀려난 먹이는 제거된다")
    void tick_removesFoodOutsideShrunkenBoundary() {
        spawnSnakesInLine(20);
        for (int i = 0; i < 10; i++) {
            world.tick(); // 반지름 = 기본 + 80
        }
        Food outside = world.spawnFoodAt(new Vec2(GameConfig.BASE_RADIUS + 50, 0));
        removeSnakesInLine(20);

        while (world.mapRadius() >= GameConfig.BASE_RADIUS + 50) {
            world.tick(); // 먹이 위치보다 안쪽으로 수축될 때까지 진행
        }

        assertThat(world.foods().stream().map(Food::id)).doesNotContain(outside.id());
    }

    /** 세로 일렬(간격 100)로 스폰해 서로 충돌·경계 이탈 없이 유지한다 */
    private void spawnSnakesInLine(int count) {
        for (int i = 0; i < count; i++) {
            world.spawnSnakeAt("line" + i, "선수" + i, new Vec2(0, -950 + i * 100.0), 0);
        }
    }

    /** spawnSnakesInLine으로 스폰한 지렁이 제거 */
    private void removeSnakesInLine(int count) {
        for (int i = 0; i < count; i++) {
            world.removeSnake("line" + i);
        }
    }
}
