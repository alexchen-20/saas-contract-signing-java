package dev.learning.contracts.web;

import dev.learning.contracts.infrai.InfraiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(InfraiException.class)
    ResponseEntity<Map<String, Object>> infraiRejection(InfraiException exception) {
        HttpStatus status = exception.httpStatus() >= 400 && exception.httpStatus() < 500
                ? HttpStatus.valueOf(exception.httpStatus())
                : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(Map.of(
                "code", exception.code(),
                "message", exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> domainRejection(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
}
