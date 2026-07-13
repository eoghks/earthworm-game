package com.rathon.snakegame.credit;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 크레딧 이력 저장소.
 */
public interface CreditHistoryRepository extends JpaRepository<CreditHistory, Long> {
}
