package com.rathon.snakegame.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 실행 구성 — 크레딧 적립 등 DB 작업을 게임 루프 스레드 밖에서 처리한다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** 크레딧 적립 전용 단일 스레드 — 적립 순서를 보존하고 DB 경합을 줄인다 */
    @Bean(name = "creditExecutor")
    public Executor creditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("credit-");
        executor.initialize();
        return executor;
    }
}
