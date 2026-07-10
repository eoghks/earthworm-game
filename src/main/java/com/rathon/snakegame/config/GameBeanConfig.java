package com.rathon.snakegame.config;

import java.util.Random;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.rathon.snakegame.game.GameWorld;

/**
 * 게임 도메인 빈 구성 — 월드는 단일 인스턴스(방 개념 없음).
 */
@Configuration
public class GameBeanConfig {

    @Bean
    public GameWorld gameWorld() {
        // 게임용 난수 — 보안 목적이 아니므로 Random으로 충분하다
        return new GameWorld(new Random());
    }
}
