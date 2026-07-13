package com.rathon.snakegame.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 전역 예외 핸들러 매핑 테스트 — 동시 중복 요청(무결성 위반)이 500이 아닌 409로 변환되는지 검증한다.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("무결성 위반: 사전 중복 검사 사이를 파고든 동시 가입은 409로 응답한다")
    void dataIntegrityViolation_mapsToConflict() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleDataIntegrity(new DataIntegrityViolationException("unique constraint"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).doesNotContain("unique"); // 내부 상세 비노출
    }
}
