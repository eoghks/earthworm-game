package com.rathon.snakegame.member;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * 인증 REST — 회원가입·로그인·로그아웃. 세션 방식이라 응답에 토큰은 없다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    /** 회원가입 요청 — WS 닉네임 정제 규칙과 일관된 길이 제한 */
    public record SignupRequest(
            @NotBlank(message = "아이디를 입력해 주세요")
            @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "아이디는 영문·숫자·밑줄 4~20자입니다")
            String username,
            @NotBlank(message = "비밀번호를 입력해 주세요")
            @Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 합니다")
            String password,
            @NotBlank(message = "닉네임을 입력해 주세요")
            @Size(max = 16, message = "닉네임은 16자 이하여야 합니다")
            String nickname) {
    }

    /** 로그인 요청 */
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    /** 인증 결과 응답 */
    public record AuthResponse(String username, String nickname) {
    }

    /** 회원가입 */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        Member member = memberService.signup(request.username(), request.password(), request.nickname().strip());
        return new AuthResponse(member.getUsername(), member.getNickname());
    }

    /** 로그인 — 인증 성공 시 SecurityContext를 세션에 저장한다 */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest login,
                              HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(login.username(), login.password()));
        // 세션 고정 공격 방지 — 로그인 성공 시 세션 id 재발급
        request.getSession().invalidate();
        request.getSession(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        String nickname = memberService.findByUsername(login.username())
                .map(Member::getNickname)
                .orElse("");
        return new AuthResponse(login.username(), nickname);
    }

    /** 로그아웃 — 세션 무효화 */
    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        request.getSession().invalidate();
        SecurityContextHolder.clearContext();
    }
}
