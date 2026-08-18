package com.pride.server.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<String> handleAiServerError(WebClientResponseException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body("AI 서버 처리 중 오류가 발생했습니다: " + e.getResponseBodyAsString());
    }
}