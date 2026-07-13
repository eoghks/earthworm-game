package com.rathon.snakegame.credit;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rathon.snakegame.game.GameConfig;
import com.rathon.snakegame.member.MemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 크레딧 서비스 — 라운드 종료 점수를 크레딧으로 환산해 적립한다.
 * 적립은 @Async(creditExecutor)로 게임 루프 스레드 밖에서 수행해 틱 지연을 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    private final MemberRepository memberRepository;
    private final CreditHistoryRepository creditHistoryRepository;

    /** 서버가 계산한 점수 → 크레딧 환산 (내림) */
    public static long toCredit(int score) {
        return score / GameConfig.CREDIT_SCORE_DIVISOR;
    }

    /**
     * 사망 보상 적립 — 게임 루프 스레드에서 호출하면 즉시 반환되고
     * 실제 DB 반영은 creditExecutor 스레드에서 트랜잭션으로 처리된다.
     */
    @Async("creditExecutor")
    @Transactional
    public void awardForScore(String username, int score) {
        long amount = toCredit(score);
        if (amount <= 0) {
            return;
        }
        // 행 잠금으로 구매 트랜잭션과의 잔액 경합을 직렬화한다
        memberRepository.findWithLockByUsername(username).ifPresentOrElse(member -> {
            member.addCredit(amount);
            creditHistoryRepository.save(CreditHistory.builder()
                    .member(member)
                    .amount(amount)
                    .reason(CreditHistory.Reason.PLAY_REWARD)
                    .build());
            log.info("크레딧 적립: memberId={}, amount={}", member.getId(), amount);
        }, () -> log.warn("크레딧 적립 대상 회원 없음")); // 아이디 등 개인정보는 로그에 남기지 않는다
    }
}
