package com.rathon.snakegame.skin;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 보유 스킨 저장소.
 */
public interface OwnedSkinRepository extends JpaRepository<OwnedSkin, Long> {

    List<OwnedSkin> findByMemberId(Long memberId);

    boolean existsByMemberIdAndSkinId(Long memberId, String skinId);
}
