package com.rathon.snakegame.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import lombok.Getter;

/**
 * 지렁이 한 마리의 상태와 이동 규칙.
 * WebSocket 계층과 무관한 순수 도메인 객체 — 단위 테스트 대상.
 */
@Getter
public class Snake {

    private final String id;
    private final String nickname;
    /** 몸 세그먼트 위치 목록 — 0번이 머리 */
    private final List<Vec2> segments = new ArrayList<>();
    /** 현재 진행 각도 (라디안) */
    private double direction;
    /** 플레이어가 지시한 목표 각도 (라디안) */
    private double targetAngle;
    /** 부스트 입력 여부 */
    private boolean boostRequested;
    /** 먹이 섭취로 예약된 성장량 (세그먼트 수) */
    private int pendingGrowth;
    /** 부스트 길이 소모 누적 틱 */
    private int boostDrainCounter;

    public Snake(String id, String nickname, Vec2 spawnPosition, double spawnAngle) {
        this.id = id;
        this.nickname = nickname;
        this.direction = spawnAngle;
        this.targetAngle = spawnAngle;
        // 머리 뒤쪽으로 일정 간격의 초기 몸을 배치한다
        Vec2 backward = Vec2.fromAngle(spawnAngle + Math.PI);
        for (int i = 0; i < GameConfig.INITIAL_LENGTH; i++) {
            segments.add(spawnPosition.plus(backward.scale(i * GameConfig.SEGMENT_SPACING)));
        }
    }

    /** 플레이어 입력 반영 */
    public void applyInput(double angle, boolean boosting) {
        this.targetAngle = angle;
        this.boostRequested = boosting;
    }

    /** 실제 부스트 발동 여부 — 최소 길이 이하면 부스트 불가 */
    public boolean isBoosting() {
        return boostRequested && segments.size() > GameConfig.MIN_BOOST_LENGTH;
    }

    /** 머리 위치 */
    public Vec2 head() {
        return segments.get(0);
    }

    /** 점수 = 현재 세그먼트 수 */
    public int score() {
        return segments.size();
    }

    /**
     * 한 틱 전진 — 회전 제한을 걸어 목표 각도로 방향을 튼 뒤 머리를 이동하고,
     * 몸은 앞 세그먼트를 일정 간격으로 따라간다.
     */
    public void move() {
        direction = turnToward(direction, targetAngle);
        double speed = isBoosting() ? GameConfig.BOOST_SPEED : GameConfig.BASE_SPEED;
        Vec2 newHead = head().plus(Vec2.fromAngle(direction).scale(speed));
        segments.set(0, newHead);
        followBody();
        applyPendingGrowth();
    }

    /** 몸 세그먼트가 앞 세그먼트를 간격 유지하며 따라간다 */
    private void followBody() {
        for (int i = 1; i < segments.size(); i++) {
            Vec2 prev = segments.get(i - 1);
            Vec2 current = segments.get(i);
            Vec2 diff = current.minus(prev);
            double dist = diff.length();
            if (dist > GameConfig.SEGMENT_SPACING) {
                segments.set(i, prev.plus(diff.scale(GameConfig.SEGMENT_SPACING / dist)));
            }
        }
    }

    /** 예약된 성장량이 있으면 꼬리를 복제해 한 틱에 1개씩 늘린다 */
    private void applyPendingGrowth() {
        if (pendingGrowth > 0) {
            segments.add(segments.get(segments.size() - 1));
            pendingGrowth--;
        }
    }

    /** 먹이 섭취 — 성장 예약 */
    public void grow(int amount) {
        pendingGrowth += amount;
    }

    /**
     * 부스트 중 길이 소모 처리 — 일정 틱마다 꼬리 세그먼트를 떼어 위치를 반환한다.
     * 반환된 위치는 월드가 먹이로 배출한다.
     */
    public Optional<Vec2> drainBoost() {
        if (!isBoosting()) {
            boostDrainCounter = 0;
            return Optional.empty();
        }
        boostDrainCounter++;
        if (boostDrainCounter < GameConfig.BOOST_DRAIN_TICKS) {
            return Optional.empty();
        }
        boostDrainCounter = 0;
        Vec2 tail = segments.remove(segments.size() - 1);
        return Optional.of(tail);
    }

    /** 읽기 전용 세그먼트 뷰 */
    public List<Vec2> segmentsView() {
        return Collections.unmodifiableList(segments);
    }

    /** 현재 각도에서 목표 각도로 틱당 최대 회전량만큼만 회전한다 */
    private static double turnToward(double current, double target) {
        double diff = normalizeAngle(target - current);
        double clamped = Math.max(-GameConfig.MAX_TURN_RATE, Math.min(GameConfig.MAX_TURN_RATE, diff));
        return normalizeAngle(current + clamped);
    }

    /** 각도를 -PI..PI 범위로 정규화 */
    private static double normalizeAngle(double angle) {
        double result = angle % (2 * Math.PI);
        if (result > Math.PI) {
            result -= 2 * Math.PI;
        }
        if (result < -Math.PI) {
            result += 2 * Math.PI;
        }
        return result;
    }
}
