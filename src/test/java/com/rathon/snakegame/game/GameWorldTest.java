package com.rathon.snakegame.game;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("경계 사망: 머리가 맵 반지름을 벗어나면 죽는다")
    void tick_killsSnakeOutOfBounds() {
        world.spawnSnakeAt("p1", "탈주자", new Vec2(GameConfig.MAP_RADIUS - 1, 0), 0);

        List<DeathEvent> deaths = world.tick();

        assertThat(deaths).extracting(DeathEvent::playerId).containsExactly("p1");
        assertThat(world.findSnake("p1")).isEmpty();
    }

    @Test
    @DisplayName("사망 변환: 죽은 지렁이 몸이 먹이로 배출된다")
    void tick_convertsDeadBodyToFood() {
        Snake snake = world.spawnSnakeAt("p1", "탈주자", new Vec2(GameConfig.MAP_RADIUS - 1, 0), 0);
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
}
