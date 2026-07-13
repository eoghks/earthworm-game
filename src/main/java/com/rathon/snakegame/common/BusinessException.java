package com.rathon.snakegame.common;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반 예외 — GlobalExceptionHandler가 HTTP 상태로 변환한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
