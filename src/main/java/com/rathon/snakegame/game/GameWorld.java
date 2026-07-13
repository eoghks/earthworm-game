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
    /** 기본 반지름 기준 먹이 목표량 — 테스트에서는 0으로 두고 결정적으로 검증한다 */
    private final int foodTarget;
    private long foodIdSequence;
    /** 현재 맵 반지름 — 접속 인원에 따라 매 틱 목표치로 보간된다 */
    private double mapRadius = GameConfig.BASE_RADIUS;

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
        beginTick();
    }

    /**
     * 틱 시작 — 이전 틱의 먹이 증분 기록을 비운다.
     * 명령 처리(killSnake 등)보다 먼저 호출해야 명령 단계에서 배출된 먹이가
     * 같은 틱의 증분 브로드캐스트에 포함된다.
     */
    public void beginTick() {
        addedFoods.clear();
        removedFoodIds.clear();
    }

    /** 랜덤 위치에 지렁이 입장 — 현재 맵 반지름 안쪽으로 스폰한다 */
    public Snake spawnSnake(String id, String nickname, String skinId) {
        double angle = random.nextDouble() * 2 * Math.PI;
        double radius = random.nextDouble() * (mapRadius - GameConfig.SPAWN_MARGIN);
        Vec2 position = Vec2.fromAngle(angle).scale(radius);
        double heading = random.nextDouble() * 2 * Math.PI;
        return spawnSnakeAt(id, nickname, skinId, position, heading);
    }

    /** 지정 위치에 기본 스킨으로 입장 — 테스트에서도 사용 */
    public Snake spawnSnakeAt(String id, String nickname, Vec2 position, double heading) {
        return spawnSnakeAt(id, nickname, GameConfig.DEFAULT_SKIN_ID, position, heading);
    }

    /** 지정 위치·스킨으로 지렁이 입장 */
    public Snake spawnSnakeAt(String id, String nickname, String skinId, Vec2 position, double heading) {
        Snake snake = new Snake(id, nickname, skinId, position, heading);
        snakes.put(id, snake);
        return snake;
    }

    /** 연결 종료 등으로 지렁이 제거 (먹이 변환 없음) */
    public void removeSnake(String id) {
        snakes.remove(id);
    }

    /** 생존 중 재입장 등 강제 사망 처리 — 몸을 먹이로 배출하며 제거한다 */
    public void killSnake(String id) {
        Optional.ofNullable(snakes.get(id))
                .ifPresent(snake -> convertToFoodAndRemove(
                        new DeathEvent(id, snake.getNickname(), snake.score())));
    }

    /** 플레이어 입력 반영 */
    public void applyInput(String id, double angle, boolean boosting) {
        Optional.ofNullable(snakes.get(id))
                .ifPresent(snake -> snake.applyInput(angle, boosting));
    }

    /**
     * 한 틱 진행: 맵 크기 조정 → 이동 → 부스트 소모 → 먹이 섭취 → 충돌/경계 사망
     * → 경계 밖 먹이 제거 → 먹이 리스폰. 사망 이벤트 목록을 반환한다.
     * 증분 기록 초기화는 beginTick()에서 별도로 수행한다.
     */
    public List<DeathEvent> tick() {
        adjustMapRadius();
        for (Snake snake : snakes.values()) {
            snake.move();
            snake.drainBoost().ifPresent(this::spawnFoodAt);
        }
        consumeFood();
        List<DeathEvent> deaths = detectDeaths();
        deaths.forEach(this::convertToFoodAndRemove);
        removeOutOfBoundsFood();
        replenishFood();
        return deaths;
    }

    /** 접속 인원 기반 목표 반지름 — 기준 인원까지는 기본 크기, 초과 시 √비례 확장 */
    public double targetRadius() {
        double scale = Math.sqrt((double) snakes.size() / GameConfig.BASE_PLAYER_COUNT);
        return GameConfig.BASE_RADIUS * Math.max(1.0, scale);
    }

    /** 현재 반지름을 목표치로 보간 — 확장은 빠르게, 수축은 느리게 */
    private void adjustMapRadius() {
        double target = targetRadius();
        if (mapRadius < target) {
            mapRadius = Math.min(target, mapRadius + GameConfig.MAP_EXPAND_PER_TICK);
        } else if (mapRadius > target) {
            mapRadius = Math.max(target, mapRadius - GameConfig.MAP_SHRINK_PER_TICK);
        }
    }

    /** 경계 밖 먹이 제거 — 수축 종료 틱에 생긴 시체 먹이 잔존을 막기 위해 매 틱 말미에 수행한다 */
    private void removeOutOfBoundsFood() {
        List<Long> outside = foods.values().stream()
                .filter(food -> food.position().length() > mapRadius)
                .map(Food::id)
                .toList();
        outside.forEach(id -> {
            foods.remove(id);
            removedFoodIds.add(id);
        });
    }

    /** 머리 근처 먹이 섭취 판정 — 먹으면 성장 예약. 굵은 머리일수록 흡수 반경이 넓어진다 */
    private void consumeFood() {
        for (Snake snake : snakes.values()) {
            Vec2 head = snake.head();
            double eatDistance = GameConfig.FOOD_EAT_DISTANCE
                    + (snake.radius() - GameConfig.BASE_SNAKE_RADIUS);
            List<Long> eaten = foods.values().stream()
                    .filter(food -> food.position().distanceTo(head) <= eatDistance)
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
            // 머리 끝(중심 + 반지름)이 경계를 넘으면 사망 — 렌더되는 머리 원과 판정을 일치시킨다
            boolean outOfBounds = snake.head().length() + snake.radius() >= mapRadius;
            if (outOfBounds || hitsOtherBody(snake)) {
                deaths.add(new DeathEvent(snake.getId(), snake.getNickname(), snake.score()));
            }
        }
        return deaths;
    }

    /** 내 머리가 다른 지렁이 몸 세그먼트에 닿았는지 — 내 머리 반지름 + 상대 몸 반지름 기준 */
    private boolean hitsOtherBody(Snake snake) {
        Vec2 head = snake.head();
        double headRadius = snake.radius();
        return snakes.values().stream()
                .filter(other -> !other.getId().equals(snake.getId()))
                .anyMatch(other -> touchesBody(head, headRadius, other));
    }

    /** 머리(반지름 headRadius)가 상대 지렁이 몸(반지름 other.radius())에 닿았는지 */
    private boolean touchesBody(Vec2 head, double headRadius, Snake other) {
        double threshold = headRadius + other.radius();
        return other.segmentsView().stream()
                .anyMatch(segment -> segment.distanceTo(head) <= threshold);
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

    /** 맵 전체 먹이 수를 면적 비례 목표량까지 채운다 */
    private void replenishFood() {
        int target = currentFoodTarget();
        while (foods.size() < target) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = random.nextDouble() * (mapRadius - GameConfig.FOOD_SPAWN_MARGIN);
            spawnFoodAt(Vec2.fromAngle(angle).scale(radius));
        }
    }

    /** 현재 맵 면적에 비례한 먹이 목표량 */
    private int currentFoodTarget() {
        double ratio = mapRadius / GameConfig.BASE_RADIUS;
        return (int) Math.round(foodTarget * ratio * ratio);
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
                .map(snake -> new LeaderboardEntry(snake.getId(), snake.getNickname(), snake.score()))
                .toList();
    }

    /** 현재 맵 반지름 */
    public double mapRadius() {
        return mapRadius;
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
