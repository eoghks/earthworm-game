package com.rathon.snakegame.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Snake 단위 테스트 — 이동·회전 제한·성장·부스트 소모 규칙 검증.
 */
class SnakeTest {

    @Test
    @DisplayName("이동: 진행 방향으로 기본 속도만큼 머리가 전진한다")
    void move_advancesHeadByBaseSpeed() {
        Snake snake = new Snake("p1", "테스터", new Vec2(0, 0), 0);

        snake.move();

        assertThat(snake.head().x()).isCloseTo(GameConfig.BASE_SPEED, within(1e-9));
        assertThat(snake.head().y()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    @DisplayName("회전 제한: 반대 방향을 지시해도 한 틱에 최대 회전량까지만 돈다")
    void move_limitsTurnRatePerTick() {
        Snake snake = new Snake("p1", "테스터", new Vec2(0, 0), 0);
        snake.applyInput(Math.PI, false);

        snake.move();

        assertThat(snake.getDirection()).isCloseTo(GameConfig.MAX_TURN_RATE, within(1e-9));
    }

    @Test
    @DisplayName("성장: 먹이 섭취 예약분만큼 다음 틱들에서 세그먼트가 늘어난다")
    void grow_addsSegmentsOverTicks() {
        Snake snake = new Snake("p1", "테스터", new Vec2(0, 0), 0);
        int initial = snake.score();

        snake.grow(3);
        snake.move();
        snake.move();
        snake.move();

        assertThat(snake.score()).isEqualTo(initial + 3);
    }

    @Test
    @DisplayName("부스트 소모: 부스트 중 일정 틱마다 꼬리가 떨어져 나간다")
    void drainBoost_removesTailPeriodically() {
        Snake snake = new Snake("p1", "테스터", new Vec2(0, 0), 0);
        growBy(snake, 5); // 길이 15 — 부스트 가능 상태로 만든다
        int lengthBeforeBoost = snake.score();
        snake.applyInput(0, true);

        int drained = 0;
        for (int i = 0; i < GameConfig.BOOST_DRAIN_TICKS; i++) {
            snake.move();
            if (snake.drainBoost().isPresent()) {
                drained++;
            }
        }

        assertThat(drained).isEqualTo(1);
        assertThat(snake.score()).isEqualTo(lengthBeforeBoost - 1);
    }

    @Test
    @DisplayName("부스트 속도: 부스트 중에는 부스트 속도로 전진한다")
    void move_usesBoostSpeedWhileBoosting() {
        Snake snake = new Snake("p1", "테스터", new Vec2(0, 0), 0);
        growBy(snake, 5);
        snake.applyInput(0, true);
        Vec2 before = snake.head();

        snake.move();

        assertThat(snake.head().distanceTo(before)).isCloseTo(GameConfig.BOOST_SPEED, within(1e-9));
    }

    @Test
    @DisplayName("최소 길이 제한: 최소 길이 이하에서는 부스트가 발동하지 않고 길이도 줄지 않는다")
    void drainBoost_stopsAtMinimumLength() {
        Snake snake = new Snake("p1", "테스터", new Vec2(0, 0), 0);
        snake.applyInput(0, true); // 초기 길이 = 최소 길이 → 부스트 불가

        assertThat(snake.isBoosting()).isFalse();
        for (int i = 0; i < GameConfig.BOOST_DRAIN_TICKS * 3; i++) {
            snake.move();
            assertThat(snake.drainBoost()).isEmpty();
        }
        assertThat(snake.score()).isEqualTo(GameConfig.INITIAL_LENGTH);
    }

    /** 성장 예약 후 틱을 돌려 실제 길이를 늘린다 */
    private static void growBy(Snake snake, int amount) {
        snake.grow(amount);
        for (int i = 0; i < amount; i++) {
            snake.move();
        }
    }
}
