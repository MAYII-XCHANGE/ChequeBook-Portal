package com.example.chequebookrequestportal.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> api(ApiException e, HttpServletRequest req) {
        return ResponseEntity.status(e.getStatus())
                .body(body(e.getCode(), e.getMessage(), Map.of(), e.isRetryable(), req));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(x -> fields.putIfAbsent(x.getField(), x.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(body("VALIDATION_ERROR", "Please correct the highlighted fields.", fields, false, req));
    }

    @ExceptionHandler({ ConstraintViolationException.class, MethodArgumentTypeMismatchException.class })
    ResponseEntity<?> invalidInput(Exception e, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(body("VALIDATION_ERROR", "One or more request values are invalid.", Map.of(), false, req));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(DataIntegrityViolationException e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body("CONFLICT", "The operation conflicts with an existing record.", Map.of(), false, req));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<?> concurrent(ObjectOptimisticLockingFailureException e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body("CONCURRENT_UPDATE",
                "This record changed while the action was being processed. Refresh and retry.", Map.of(), true, req));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> denied(AccessDeniedException e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body("FORBIDDEN", "You are not authorized to perform this action.", Map.of(), false, req));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unknown(Exception e, HttpServletRequest req) {
        return ResponseEntity.status(500)
                .body(body("INTERNAL_ERROR", "An unexpected error occurred.", Map.of(), true, req));
    }

    private Map<String, Object> body(String code, String message, Map<String, String> fields, boolean retryable,
            HttpServletRequest req) {
        return Map.of("code", code, "message", message, "fieldErrors", fields, "retryable", retryable, "correlationId",
                String.valueOf(req.getAttribute("correlationId")));
    }
}
