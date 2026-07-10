package com.rathon.snakegame.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import lombok.Getter;

/**
 * 게임 월드 — 지렁이·먹이 전체 상태와 틱 진행 로직.
 * WebSocket 계층과 분리된 순수 Java 클래스로, 게임 루프 스레드에서만 변경한다.
 */
public class GameWorld {

    private final Map<String, Snake> snakes = new LinkedHashMap<>();
    private final Map<Long, Food> foods = new LinkedHashMap<>();
    private final Random random;
    /** 맵에 유지할 먹이 목표량 — 테스트에서는 0으로 두고 결정적으로 검증한다 */
    private final int foodTarget;
    private long foodIdSequence;

    /** 이번 틱에 추가된 먹이 — 증분 브로드캐스트용 */
    @Getter
    private final List<Food> addedFoods = new ArrayList<>();
    /** 이번 틱에 제거된 먹이 id — 증분 브로드캐스트용 */
    @Getter
    private final List<Long> removedFoodIds = new ArrayList<>();

    public GameWorld(Random random) {
        this(random, GameConfig.FOOD_COUNT);
    }

    public GameWorld(Random random, int foodTarget) {
        this.random = random;
        this.foodTarget = foodTarget;
        replenishFood();
        // 초기 먹이는 증분이 아니라 스냅샷으로 전달되므로 증분 기록을 비운다
        addedFoods.clear();
    }

    /** 랜덤 위치에 지렁이 입장 */
    public Snake spawnSnake(String id, String nickname) {
        double angle = random.nextDouble() * 2 * Math.PI;
        double radius = random.nextDouble() * (GameConfig.MAP_RADIUS - GameConfig.SPAWN_MARGIN);
        Vec2 position = Vec2.fromAngle(angle).scale(radius);
        double heading = random.nextDouble() * 2 * Math.PI;
        return spawnSnakeAt(id, nickname, position, heading);
    }

    /** 지정 위치에 지렁이 입장 — 테스트에서도 사용 */
    public Snake spawnSnakeAt(String id, String nickname, Vec2 position, double heading) {
        Snake snake = new Snake(id, nickname, position, heading);
        snakes.put(id, snake);
        return snake;
    }

    /** 연결 종료 등으로 지렁이 제거 (먹이 변환 없음) */
    public void removeSnake(String id) {
        snakes.remove(id);
    }

    /** 플레이어 입력 반영 */
    public void applyInput(String id, double angle, boolean boosting) {
        Optional.ofNullable(snakes.get(id))
                .ifPresent(snake -> snake.applyInput(angle, boosting));
    }

    /**
     * 한 틱 진행: 이동 → 부스트 소모 → 먹이 섭취 → 충돌/경계 사망 → 먹이 리스폰.
     * 사망 이벤트 목록을 반환한다.
     */
    public List<DeathEvent> tick() {
        addedFoods.clear();
        removedFoodIds.clear();
        for (Snake snake : snakes.values()) {
            snake.move();
            snake.drainBoost().ifPresent(this::spawnFoodAt);
        }
        consumeFood();
        List<DeathEvent> deaths = detectDeaths();
        deaths.forEach(this::convertToFoodAndRemove);
        replenishFood();
        return deaths;
    }

    /** 머리 근처 먹이 섭취 판정 — 먹으면 성장 예약 */
    private void consumeFood() {
        for (Snake snake : snakes.values()) {
            Vec2 head = snake.head();
            List<Long> eaten = foods.values().stream()
                    .filter(food -> food.position().distanceTo(head) <= GameConfig.FOOD_EAT_DISTANCE)
                    .map(Food::id)
                    .toList();
            eaten.forEach(id -> {
                foods.remove(id);
                removedFoodIds.add(id);
                snake.grow(GameConfig.GROWTH_PER_FOOD);
            });
        }
    }

    /** 경계 이탈·머리-몸 충돌 사망 판정 */
    private List<DeathEvent> detectDeaths() {
        List<DeathEvent> deaths = new ArrayList<>();
        for (Snake snake : snakes.values()) {
            boolean outOfBounds = snake.head().length() >= GameConfig.MAP_RADIUS;
            if (outOfBounds || hitsOtherBody(snake)) {
                deaths.add(new DeathEvent(snake.getId(), snake.getNickname(), snake.score()));
            }
        }
        return deaths;
    }

    /** 내 머리가 다른 지렁이 몸 세그먼트에 닿았는지 */
    private boolean hitsOtherBody(Snake snake) {
        Vec2 head = snake.head();
        return snakes.values().stream()
                .filter(other -> !other.getId().equals(snake.getId()))
                .flatMap(other -> other.segmentsView().stream())
                .anyMatch(segment -> segment.distanceTo(head) <= GameConfig.COLLISION_DISTANCE);
    }

    /** 사망한 지렁이 몸을 먹이로 변환하고 월드에서 제거 */
    private void convertToFoodAndRemove(DeathEvent death) {
        Optional.ofNullable(snakes.remove(death.playerId()))
                .ifPresent(snake -> {
                    List<Vec2> body = snake.segmentsView();
                    for (int i = 0; i < body.size(); i += GameConfig.CORPSE_FOOD_INTERVAL) {
                        spawnFoodAt(body.get(i));
                    }
                });
    }

    /** 맵 전체 먹이 수를 목표량까지 채운다 */
    private void replenishFood() {
        while (foods.size() < foodTarget) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = random.nextDouble() * (GameConfig.MAP_RADIUS - 50);
            spawnFoodAt(Vec2.fromAngle(angle).scale(radius));
        }
    }

    /** 지정 위치에 먹이 생성 — 테스트에서도 사용 */
    public Food spawnFoodAt(Vec2 position) {
        Food food = new Food(++foodIdSequence, position);
        foods.put(food.id(), food);
        addedFoods.add(food);
        return food;
    }

    /** 길이 기준 상위 N명 리더보드 */
    public List<LeaderboardEntry> leaderboard() {
        return snakes.values().stream()
                .sorted(Comparator.comparingInt(Snake::score).reversed())
                .limit(GameConfig.LEADERBOARD_SIZE)
                .map(snake -> new LeaderboardEntry(snake.getNickname(), snake.score()))
                .toList();
    }

    public Collection<Snake> snakes() {
        return snakes.values();
    }

    public Collection<Food> foods() {
        return foods.values();
    }

    public Optional<Snake> findSnake(String id) {
        return Optional.ofNullable(snakes.get(id));
    }
}
