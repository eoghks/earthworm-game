package com.rathon.snakegame.member;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rathon.snakegame.common.BusinessException;
import com.rathon.snakegame.skin.OwnedSkin;
import com.rathon.snakegame.skin.OwnedSkinRepository;
import com.rathon.snakegame.skin.SkinCatalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 서비스 — 가입·조회. 비밀번호는 BCrypt로 해싱하고 로그에 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final OwnedSkinRepository ownedSkinRepository;
    private final PasswordEncoder passwordEncoder;

    /** 회원가입 — username 중복 거부, 기본 스킨 자동 보유 */
    @Transactional
    public Member signup(String username, String rawPassword, String nickname) {
        if (memberRepository.existsByUsername(username)) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다");
        }
        Member member = memberRepository.save(Member.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .nickname(nickname)
                .build());
        ownedSkinRepository.save(OwnedSkin.builder()
                .member(member)
                .skinId(SkinCatalog.DEFAULT.getId())
                .build());
        log.info("회원가입 완료: memberId={}", member.getId()); // 아이디·비밀번호 등 개인정보는 로그에 남기지 않는다
        return member;
    }

    /** username으로 회원 조회 */
    @Transactional(readOnly = true)
    public Optional<Member> findByUsername(String username) {
        return memberRepository.findByUsername(username);
    }

    /** 내 정보 조회 — 크레딧·보유 스킨·장착 스킨 */
    @Transactional(readOnly = true)
    public MyInfo myInfo(String username) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "회원을 찾을 수 없습니다"));
        List<String> ownedSkinIds = ownedSkinRepository.findByMemberId(member.getId()).stream()
                .map(OwnedSkin::getSkinId)
                .toList();
        return new MyInfo(member.getUsername(), member.getNickname(),
                member.getCredit(), ownedSkinIds, member.getEquippedSkinId());
    }

    /** 내 정보 응답 DTO */
    public record MyInfo(String username, String nickname, long credit,
                         List<String> ownedSkinIds, String equippedSkinId) {
    }
}
