package com.rathon.snakegame.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 크레딧 실행기 구성 테스트 — 큐 포화가 제출자(게임 루프)로 역류하지 않고,
 * 서버 종료 시 큐 대기 적립분이 유실되지 않는지 검증한다.
 */
class AsyncConfigTest {

    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = (ThreadPoolTaskExecutor) new AsyncConfig().creditExecutor();
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    @DisplayName("큐 포화: 초과 제출은 예외 없이 폐기된다 — 게임 루프 틱이 중단되지 않는다")
    void rejectedTask_doesNotPropagateToSubmitter() {
        CountDownLatch blocker = new CountDownLatch(1);
        executor.execute(() -> awaitQuietly(blocker)); // 단일 워커 스레드 점유
        for (int i = 0; i < 1000; i++) {
            executor.execute(() -> { }); // 큐 용량(1000)까지 채운다
        }
        // 포화 상태 초과 제출 — AbortPolicy였다면 RejectedExecutionException이 여기서 터진다
        assertThatCode(() -> executor.execute(() -> { })).doesNotThrowAnyException();
        blocker.countDown();
    }

    @Test
    @DisplayName("종료: 큐에 대기 중인 작업을 처리한 뒤 종료한다 — 적립 유실 방지")
    void shutdown_drainsQueuedTasksBeforeTermination() {
        AtomicBoolean queuedTaskExecuted = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);
        executor.execute(() -> {
            started.countDown();
            sleepQuietly(200); // 워커가 처리 중인 동안 다음 작업은 큐에 대기
        });
        executor.execute(() -> queuedTaskExecuted.set(true));
        awaitQuietly(started);

        executor.shutdown(); // waitForTasksToCompleteOnShutdown=true → 큐 소진까지 대기

        assertThat(queuedTaskExecuted).isTrue();
    }

    /** 인터럽트는 테스트 실패로 드러나므로 플래그 복원만 한다 (흐름 제어 아님) */
    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 인터럽트는 테스트 실패로 드러나므로 플래그 복원만 한다 (흐름 제어 아님) */
    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
