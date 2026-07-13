package com.rathon.snakegame.credit;

import java.time.LocalDateTime;

import com.rathon.snakegame.member.Member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 크레딧 변동 이력 — 적립(플레이 보상)·사용(스킨 구매)을 기록한다.
 */
@Entity
@Table(name = "credit_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    /** 변동량 — 적립은 양수, 사용은 음수 */
    @Column(nullable = false)
    private long amount;

    /** 변동 사유 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Reason reason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private CreditHistory(Member member, long amount, Reason reason) {
        this.member = member;
        this.amount = amount;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    /** 변동 사유 구분 */
    public enum Reason {
        /** 라운드 종료 점수 환산 적립 */
        PLAY_REWARD,
        /** 스킨 구매 차감 */
        SKIN_PURCHASE
    }
}
