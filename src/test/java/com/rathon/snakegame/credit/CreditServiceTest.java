package com.rathon.snakegame.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.rathon.snakegame.member.Member;
import com.rathon.snakegame.member.MemberRepository;

/**
 * 크레딧 서비스 테스트 — 점수 환산·적립.
 * 슬라이스 테스트에서는 @EnableAsync가 없어 awardForScore가 동기 실행되므로 결정적으로 검증한다.
 */
@DataJpaTest
@Import(CreditService.class)
class CreditServiceTest {

    @Autowired
    private CreditService creditService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CreditHistoryRepository creditHistoryRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
                .username("player1")
                .password("{bcrypt}dummy")
                .nickname("플레이어")
                .build());
    }

    @Test
    @DisplayName("환산: 점수 10점당 크레딧 1 — 내림")
    void toCredit_floorsScoreByDivisor() {
        assertThat(CreditService.toCredit(0)).isZero();
        assertThat(CreditService.toCredit(9)).isZero();
        assertThat(CreditService.toCredit(10)).isEqualTo(1);
        assertThat(CreditService.toCredit(157)).isEqualTo(15);
    }

    @Test
    @DisplayName("적립: 사망 점수를 환산해 잔액에 더하고 이력을 남긴다")
    void awardForScore_addsCreditAndHistory() {
        creditService.awardForScore("player1", 157);

        Member reloaded = memberRepository.findByUsername("player1").orElseThrow();
        assertThat(reloaded.getCredit()).isEqualTo(15);
        assertThat(creditHistoryRepository.findAll())
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getAmount()).isEqualTo(15);
                    assertThat(history.getReason()).isEqualTo(CreditHistory.Reason.PLAY_REWARD);
                });
    }

    @Test
    @DisplayName("적립: 환산 결과 0이면 잔액·이력 모두 변화 없음")
    void awardForScore_skipsZeroAmount() {
        creditService.awardForScore("player1", 9);

        assertThat(memberRepository.findByUsername("player1").orElseThrow().getCredit()).isZero();
        assertThat(creditHistoryRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("적립: 존재하지 않는 회원이면 예외 없이 건너뛴다")
    void awardForScore_ignoresUnknownMember() {
        assertThatCode(() -> creditService.awardForScore("ghost", 100))
                .doesNotThrowAnyException();
        assertThat(creditHistoryRepository.findAll()).isEmpty();
    }
}
