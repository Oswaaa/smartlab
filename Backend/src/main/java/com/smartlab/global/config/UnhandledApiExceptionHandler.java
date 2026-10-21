package com.smartlab.global.config;

import com.smartlab.management.dto.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 把未进入控制器 catch 的异常打到日志，并把异常类型带回前端。
 * 否则 Spring 默认的 500 响应没有 message，页面只能看到状态码。
 */
@RestControllerAdvice
public class UnhandledApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UnhandledApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handle(Exception exception, HttpServletRequest request) throws Exception {
        if (exception instanceof AccessDeniedException || exception instanceof ResponseStatusException) {
            throw exception;
        }
        log.error("接口未捕获异常 {} {}", request.getMethod(), request.getRequestURI(), exception);
        String detail = exception.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = exception.getClass().getSimpleName();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(exception.getClass().getSimpleName() + ": " + detail));
    }
}
