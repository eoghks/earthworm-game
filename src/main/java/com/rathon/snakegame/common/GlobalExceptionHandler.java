package com.rathon.snakegame.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * REST 예외 → JSON 오류 응답 변환. 내부 상세는 노출하지 않는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 오류 응답 본문 */
    public record ErrorResponse(String message) {
    }

    /** 비즈니스 규칙 위반 — 예외에 지정된 상태 코드로 응답 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getMessage()));
    }

    /** 로그인 실패 등 인증 오류 — 계정 존재 여부를 구분할 수 없게 단일 메시지로 응답 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("아이디 또는 비밀번호가 올바르지 않습니다"));
    }

    /** 유니크 제약 등 무결성 위반 — 사전 검사 사이를 파고든 동시 중복 요청을 409로 응답 (500 노출 방지) */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("이미 처리되었거나 중복된 요청입니다"));
    }

    /** 요청 본문 파싱 실패(잘못된 JSON 등) — 400으로 응답 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("요청 본문이 올바르지 않습니다"));
    }

    /** Bean Validation 실패 — 첫 번째 필드 오류 메시지만 노출 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }
}
