package com.rathon.snakegame.member;

import com.rathon.snakegame.skin.SkinCatalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 엔티티 — 로그인 계정·크레딧 잔액·장착 스킨을 보유한다.
 */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 id — 유일 */
    @Column(nullable = false, unique = true, length = 20)
    private String username;

    /** BCrypt 해시된 비밀번호 */
    @Column(nullable = false)
    private String password;

    /** 게임 표시 닉네임 */
    @Column(nullable = false, length = 16)
    private String nickname;

    /** 크레딧 잔액 */
    @Column(nullable = false)
    private long credit;

    /** 장착 중인 스킨 id — 항상 값이 있다 (기본 스킨) */
    @Column(nullable = false, length = 32)
    private String equippedSkinId;

    @Builder
    private Member(String username, String password, String nickname) {
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.credit = 0;
        this.equippedSkinId = SkinCatalog.DEFAULT.getId();
    }

    /** 크레딧 적립 */
    public void addCredit(long amount) {
        this.credit += amount;
    }

    /** 크레딧 차감 — 잔액 부족 검증은 서비스에서 선행한다 */
    public void spendCredit(long amount) {
        this.credit -= amount;
    }

    /** 스킨 장착 — 보유 검증은 서비스에서 선행한다 */
    public void equipSkin(String skinId) {
        this.equippedSkinId = skinId;
    }
}
