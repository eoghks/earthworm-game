package com.rathon.snakegame.member;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * 회원 REST — 내 정보(크레딧·보유 스킨·장착 스킨) 조회.
 */
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /** 내 정보 — 인증 필수 (SecurityConfig에서 보호) */
    @GetMapping("/me")
    public MemberService.MyInfo me(Principal principal) {
        return memberService.myInfo(principal.getName());
    }
}
