package com.rathon.snakegame.member;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * 회원 저장소 — 파라미터 바인딩되는 파생 쿼리만 사용한다.
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByUsername(String username);

    boolean existsByUsername(String username);

    /** 크레딧 차감·적립 등 잔액 변경 트랜잭션용 — 행 잠금으로 동시 요청 경합을 직렬화한다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Member> findWithLockByUsername(@Param("username") String username);
}
