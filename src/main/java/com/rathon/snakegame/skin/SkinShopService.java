package com.rathon.snakegame.skin;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rathon.snakegame.common.BusinessException;
import com.rathon.snakegame.credit.CreditHistory;
import com.rathon.snakegame.credit.CreditHistoryRepository;
import com.rathon.snakegame.member.Member;
import com.rathon.snakegame.member.MemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 스킨 상점 — 구매(크레딧 차감)·장착.
 * 구매는 회원 행 잠금으로 동시 요청 간 잔액 이중 차감을 막고,
 * (회원, 스킨) 유니크 제약이 중복 구매를 이중으로 차단한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkinShopService {

    private final MemberRepository memberRepository;
    private final OwnedSkinRepository ownedSkinRepository;
    private final CreditHistoryRepository creditHistoryRepository;

    /** 스킨 구매 — 잔액 부족·중복 구매 거부 */
    @Transactional
    public void purchase(String username, String skinId) {
        SkinCatalog skin = findSkin(skinId);
        Member member = findMemberWithLock(username);
        if (ownedSkinRepository.existsByMemberIdAndSkinId(member.getId(), skin.getId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 보유한 스킨입니다");
        }
        if (member.getCredit() < skin.getPrice()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "크레딧이 부족합니다");
        }
        member.spendCredit(skin.getPrice());
        ownedSkinRepository.save(OwnedSkin.builder().member(member).skinId(skin.getId()).build());
        creditHistoryRepository.save(CreditHistory.builder()
                .member(member)
                .amount(-skin.getPrice())
                .reason(CreditHistory.Reason.SKIN_PURCHASE)
                .build());
        log.info("스킨 구매: memberId={}, skin={}", member.getId(), skin.getId());
    }

    /** 스킨 장착 — 보유하지 않은 스킨은 거부 */
    @Transactional
    public void equip(String username, String skinId) {
        SkinCatalog skin = findSkin(skinId);
        Member member = findMemberWithLock(username);
        if (!ownedSkinRepository.existsByMemberIdAndSkinId(member.getId(), skin.getId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "보유하지 않은 스킨입니다");
        }
        member.equipSkin(skin.getId());
    }

    /** 카탈로그에서 스킨 조회 — 없는 id는 거부 */
    private SkinCatalog findSkin(String skinId) {
        return SkinCatalog.findById(skinId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 스킨입니다"));
    }

    /** 잔액 변경 트랜잭션용 회원 행 잠금 조회 */
    private Member findMemberWithLock(String username) {
        return memberRepository.findWithLockByUsername(username)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "회원을 찾을 수 없습니다"));
    }
}
