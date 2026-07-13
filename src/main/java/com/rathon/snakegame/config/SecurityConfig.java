package com.rathon.snakegame.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import jakarta.servlet.DispatcherType;

/**
 * 시큐리티 구성 — 게스트 플레이(정적 리소스·/ws)는 개방하고 회원 API만 보호한다.
 *
 * CSRF 비활성 근거:
 * - 이 API는 브라우저 폼이 아닌 JSON REST + WebSocket만 사용하고,
 *   세션 쿠키를 SameSite=Lax로 강제해(application.properties) 크로스사이트 POST에
 *   쿠키가 실리지 않으므로 고전적 CSRF 벡터가 차단된다.
 * - WebSocket은 핸드셰이크 허용 오리진 제한(WebSocketConfig)으로 별도 방어한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // 오류 디스패치(/error 포워드)는 인가 제외 — 본래 상태코드(400/500)가 401로 마스킹되지 않게 한다
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // 게임 클라이언트 정적 리소스 + WebSocket — 게스트 플레이 유지
                        .requestMatchers("/", "/index.html", "/game.js", "/style.css", "/favicon.ico", "/ws").permitAll()
                        // 인증 API·스킨 카탈로그(게스트도 타 플레이어 스킨 렌더링에 필요)는 공개
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/skins").permitAll()
                        // 내 정보·구매·장착 등 나머지 API는 로그인 필수
                        .anyRequest().authenticated())
                // REST 특성상 로그인 페이지 리다이렉트 대신 401을 내려준다
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }

    /** 비밀번호 해싱 — BCrypt */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 로그인 컨트롤러에서 사용할 인증 관리자 */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /** SecurityContext를 HTTP 세션에 저장 — 세션 방식 로그인 */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
