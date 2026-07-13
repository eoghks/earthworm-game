package com.rathon.snakegame.skin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import com.rathon.snakegame.common.BusinessException;
import com.rathon.snakegame.member.Member;
import com.rathon.snakegame.member.MemberRepository;

/**
 * 스킨 상점 테스트 — 구매(잔액 부족·중복 거부)·장착(미보유 거부).
 */
@DataJpaTest
@Import(SkinShopService.class)
class SkinShopServiceTest {

    @Autowired
    private SkinShopService skinShopService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private OwnedSkinRepository ownedSkinRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
                .username("buyer")
                .password("{bcrypt}dummy")
                .nickname("구매자")
                .build());
        member.addCredit(100);
    }

    @Test
    @DisplayName("구매: 크레딧을 차감하고 보유 목록에 추가한다")
    void purchase_deductsCreditAndOwnsSkin() {
        skinShopService.purchase("buyer", SkinCatalog.CRIMSON.getId());

        assertThat(member.getCredit()).isEqualTo(100 - SkinCatalog.CRIMSON.getPrice());
        assertThat(ownedSkinRepository.existsByMemberIdAndSkinId(member.getId(), "crimson")).isTrue();
    }

    @Test
    @DisplayName("구매 거부: 잔액 부족이면 400이며 아무것도 변하지 않는다")
    void purchase_rejectsInsufficientCredit() {
        assertThatThrownBy(() -> skinShopService.purchase("buyer", SkinCatalog.OCEAN_GRADIENT.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(member.getCredit()).isEqualTo(100);
        assertThat(ownedSkinRepository.findByMemberId(member.getId())).isEmpty();
    }

    @Test
    @DisplayName("구매 거부: 이미 보유한 스킨은 409이며 이중 차감되지 않는다")
    void purchase_rejectsDuplicate() {
        skinShopService.purchase("buyer", SkinCatalog.CRIMSON.getId());
        long creditAfterFirst = member.getCredit();

        assertThatThrownBy(() -> skinShopService.purchase("buyer", SkinCatalog.CRIMSON.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(member.getCredit()).isEqualTo(creditAfterFirst);
    }

    @Test
    @DisplayName("구매 거부: 카탈로그에 없는 스킨은 404")
    void purchase_rejectsUnknownSkin() {
        assertThatThrownBy(() -> skinShopService.purchase("buyer", "no-such-skin"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("장착: 보유한 스킨으로 장착 스킨이 바뀐다")
    void equip_changesEquippedSkin() {
        skinShopService.purchase("buyer", SkinCatalog.EMERALD.getId());

        skinShopService.equip("buyer", SkinCatalog.EMERALD.getId());

        assertThat(member.getEquippedSkinId()).isEqualTo("emerald");
    }

    @Test
    @DisplayName("장착 거부: 보유하지 않은 스킨은 400이며 장착이 유지된다")
    void equip_rejectsNotOwnedSkin() {
        assertThatThrownBy(() -> skinShopService.equip("buyer", SkinCatalog.GOLD.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(member.getEquippedSkinId()).isEqualTo(SkinCatalog.DEFAULT.getId());
    }
}
