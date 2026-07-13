package com.rathon.snakegame.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

/**
 * 비동기 실행 구성 — 크레딧 적립 등 DB 작업을 게임 루프 스레드 밖에서 처리한다.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /** 서버 종료 시 큐에 남은 적립 작업을 소진할 최대 대기 시간(초) */
    private static final int SHUTDOWN_AWAIT_SECONDS = 30;

    /**
     * 크레딧 적립 전용 단일 스레드 — 적립 순서를 보존하고 DB 경합을 줄인다.
     * - 종료 시 큐 대기분을 끝까지 처리해 적립 유실을 막는다.
     * - 큐 포화 시 예외를 호출자(게임 루프 스레드)에 던지지 않고 경고 후 폐기해 틱 중단을 막는다.
     *   (CallerRunsPolicy는 게임 루프 스레드에서 DB를 실행하게 되므로 금지)
     */
    @Bean(name = "creditExecutor")
    public Executor creditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("credit-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_AWAIT_SECONDS);
        executor.setRejectedExecutionHandler(AsyncConfig::discardWithWarning);
        executor.initialize();
        return executor;
    }

    /** 거부된 작업은 경고만 남기고 폐기 — 제출자(게임 루프)로 예외가 역류하지 않게 한다 */
    private static void discardWithWarning(Runnable task, ThreadPoolExecutor pool) {
        log.warn("비동기 작업 큐 포화·종료로 폐기: queueSize={}", pool.getQueue().size());
    }

    /** @Async 실행 중 예외 — 기본 핸들러는 로그 레벨이 낮아 적립 실패를 error로 명시한다 */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // 파라미터에 username 등 개인정보가 있을 수 있어 메서드명만 남긴다
        return (ex, method, params) -> log.error("비동기 작업 실패: method={}", method.getName(), ex);
    }
}
