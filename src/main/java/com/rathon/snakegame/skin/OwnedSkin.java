package com.rathon.snakegame.skin;

import com.rathon.snakegame.member.Member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 보유 스킨 — (회원, 스킨) 유니크 제약으로 중복 구매를 DB 차원에서도 차단한다.
 */
@Entity
@Table(name = "owned_skin",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "skin_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OwnedSkin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    /** SkinCatalog의 스킨 id */
    @Column(name = "skin_id", nullable = false, length = 32)
    private String skinId;

    @Builder
    private OwnedSkin(Member member, String skinId) {
        this.member = member;
        this.skinId = skinId;
    }
}
