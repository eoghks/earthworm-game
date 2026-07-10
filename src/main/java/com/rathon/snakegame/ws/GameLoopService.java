package com.rathon.snakegame.ws;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.SessionLimitExceededException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rathon.snakegame.game.DeathEvent;
import com.rathon.snakegame.game.GameConfig;
import com.rathon.snakegame.game.GameWorld;
import com.rathon.snakegame.protocol.DeadMessage;
import com.rathon.snakegame.protocol.FoodDto;
import com.rathon.snakegame.protocol.JoinedMessage;
import com.rathon.snakegame.protocol.SnakeDto;
import com.rathon.snakegame.protocol.StateMessage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 게임 루프 — 전용 스케줄러로 초당 20틱 진행.
 * 명령 큐를 비우고 월드를 진행시킨 뒤 사망 통지·상태 브로드캐스트를 수행한다.
 * 월드 변경과 메시지 전송은 모두 이 루프 스레드에서만 일어난다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameLoopService {

    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final GameWorld world;
    private final ConcurrentLinkedQueue<GameCommand> commands = new ConcurrentLinkedQueue<>();
    private ScheduledExecutorService scheduler;

    /** WebSocket 스레드에서 명령을 큐에 적재한다 */
    public void enqueue(GameCommand command) {
        commands.offer(command);
    }

    @PostConstruct
    void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "game-loop");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::tickSafely,
                GameConfig.TICK_INTERVAL_MS, GameConfig.TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        log.info("게임 루프 시작: {}틱/초", GameConfig.TICK_RATE);
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }

    /** 틱 중 예외로 루프가 죽지 않도록 방어한다 (흐름 제어 아님) */
    private void tickSafely() {
        try {
            tick();
        } catch (Exception e) {
            log.error("게임 틱 처리 중 오류", e);
        }
    }

    /** 한 틱: 증분 초기화 → 명령 처리 → 월드 진행 → 사망 통지 → 상태 브로드캐스트 */
    private void tick() {
        // 명령 처리 전에 증분 버퍼를 비워야 명령 단계(재입장 killSnake 등)의 먹이 배출이 브로드캐스트에 포함된다
        world.beginTick();
        drainCommands();
        List<DeathEvent> deaths = world.tick();
        deaths.forEach(this::notifyDeath);
        broadcastState();
    }

    /** 큐에 쌓인 입장·입력·퇴장 명령을 모두 처리한다 */
    private void drainCommands() {
        GameCommand command;
        while ((command = commands.poll()) != null) {
            switch (command) {
                case GameCommand.Join join -> handleJoin(join);
                case GameCommand.Input input -> handleInput(input);
                case GameCommand.Leave leave -> handleLeave(leave);
            }
        }
    }

    /** 입장 처리 — 생존 중 재입장은 사망 처리(먹이 배출) 후 새로 스폰하고 입장 승인 메시지를 보낸다 */
    private void handleJoin(GameCommand.Join join) {
        sessionRegistry.findPlayerBySession(join.sessionId()).ifPresent(oldPlayerId -> {
            world.killSnake(oldPlayerId);
            sessionRegistry.unbindPlayer(oldPlayerId);
        });
        String playerId = UUID.randomUUID().toString();
        world.spawnSnake(playerId, join.nickname());
        sessionRegistry.bindPlayer(join.sessionId(), playerId);
        List<FoodDto> foods = world.foods().stream().map(FoodDto::from).toList();
        sessionRegistry.findSession(join.sessionId())
                .ifPresent(session -> send(session, JoinedMessage.of(playerId, world.mapRadius(), foods)));
    }

    /** 조작 입력 반영 */
    private void handleInput(GameCommand.Input input) {
        sessionRegistry.findPlayerBySession(input.sessionId())
                .ifPresent(playerId -> world.applyInput(playerId, input.angle(), input.boosting()));
    }

    /** 연결 종료 처리 — 지렁이 제거 후 세션 등록 해제 */
    private void handleLeave(GameCommand.Leave leave) {
        sessionRegistry.findPlayerBySession(leave.sessionId())
                .ifPresent(world::removeSnake);
        sessionRegistry.unregister(leave.sessionId());
    }

    /** 사망한 플레이어에게 통지하고 바인딩을 해제한다 (세션 유지 — 재입장 가능) */
    private void notifyDeath(DeathEvent death) {
        sessionRegistry.findSessionByPlayer(death.playerId())
                .ifPresent(session -> send(session, DeadMessage.of(death.score())));
        sessionRegistry.unbindPlayer(death.playerId());
    }

    /** 전체 상태(지렁이·먹이 증분·리더보드)를 모든 세션에 브로드캐스트한다 */
    private void broadcastState() {
        List<SnakeDto> snakes = world.snakes().stream().map(SnakeDto::from).toList();
        List<FoodDto> foodsAdded = world.getAddedFoods().stream().map(FoodDto::from).toList();
        StateMessage state = StateMessage.of(world.mapRadius(),
                snakes, foodsAdded, List.copyOf(world.getRemovedFoodIds()), world.leaderboard());
        // 개선 여지: 접속자가 많아지면 시야 기반 관심 영역(AOI) 필터링으로 페이로드를 더 줄일 수 있다
        TextMessage payload = new TextMessage(toJson(state));
        sessionRegistry.allSessions().stream()
                .filter(WebSocketSession::isOpen)
                .forEach(session -> sendRaw(session, payload));
    }

    /** 객체를 JSON으로 직렬화해 전송한다 */
    private void send(WebSocketSession session, Object message) {
        sendRaw(session, new TextMessage(toJson(message)));
    }

    /** 전송 오류는 세션 단위로 격리 — 남은 세션의 브로드캐스트가 중단되지 않게 한다 (신뢰 경계 방어) */
    private void sendRaw(WebSocketSession session, TextMessage message) {
        try {
            session.sendMessage(message);
        } catch (SessionLimitExceededException e) {
            // 전송 시간·버퍼 한도 초과 — 해당 세션만 끊어 게임 루프를 보호한다
            log.warn("전송 한도 초과로 세션 종료: session={}", session.getId());
            closeQuietly(session);
        } catch (IOException | IllegalStateException e) {
            // 닫힌 세션 race 등 — 종료 이벤트에서 정리된다
            log.warn("메시지 전송 실패: session={}", session.getId());
        }
    }

    /** 세션 강제 종료 — 종료 실패는 로깅만 (연결 종료 이벤트로 플레이어가 정리된다) */
    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (IOException e) {
            log.debug("세션 종료 중 오류: session={}", session.getId());
        }
    }

    /** 직렬화 실패는 프로그래밍 오류이므로 런타임 예외로 전환한다 */
    private String toJson(Object message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("메시지 직렬화 실패", e);
        }
    }
}
