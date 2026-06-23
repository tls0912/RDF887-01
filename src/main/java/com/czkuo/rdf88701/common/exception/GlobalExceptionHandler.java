package com.czkuo.rdf88701.common.exception;

import com.czkuo.rdf88701.common.dto.ResponseResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * 全域錯誤處理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 讓 ResponseStatusException 維持原本的狀態碼與訊息
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ResponseResult<?>> handle(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity
                .status(status)
                .body(ResponseResult.fail(ex.getReason() != null ? ex.getReason() : ex.getMessage(), status.value()));
    }

    // 參數驗證錯誤（@Valid）
    @ExceptionHandler({ MethodArgumentNotValidException.class, BindException.class, IllegalArgumentException.class })
    public ResponseEntity<ResponseResult<?>> handleBadRequest(Exception ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String msg;
        if (ex instanceof MethodArgumentNotValidException manve) {
            msg = manve.getBindingResult().getFieldErrors().stream()
                    .map(e -> e.getField() + " " + e.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        } else if (ex instanceof BindException be) {
            msg = be.getBindingResult().getFieldErrors().stream()
                    .map(e -> e.getField() + " " + e.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        } else {
            msg = ex.getMessage();
        }
        return ResponseEntity
                .status(status)
                .body(ResponseResult.fail(msg != null ? msg : "Bad request.", status.value()));
    }

    // 業務型錯誤（你原本的 IllegalStateException）
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ResponseResult<?>> handleIllegalState(IllegalStateException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity
                .status(status)
                .body(ResponseResult.fail("Business Error: " + ex.getMessage(), status.value()));
    }

    // 唯一鍵 / 資料庫約束衝突 → 409
    @ExceptionHandler({ DuplicateKeyException.class, DataIntegrityViolationException.class })
    public ResponseEntity<ResponseResult<?>> handleConflict(RuntimeException ex) {
        HttpStatus status = HttpStatus.CONFLICT;
        String msg = rootMessage(ex);
        return ResponseEntity
                .status(status)
                .body(ResponseResult.fail(msg != null ? msg : "Conflict.", status.value()));
    }

    // 查無資料 → 404（依你專案可加上自己的 NotFoundException）
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ResponseResult<?>> handleNotFound(NoSuchElementException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity
                .status(status)
                .body(ResponseResult.fail(ex.getMessage() != null ? ex.getMessage() : "Not found.", status.value()));
    }

    // 其他未預期錯誤 → 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseResult<?>> handleUnknownException(Exception ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(status)
                .body(ResponseResult.fail("Internal Error: " + ex.getMessage(), status.value()));
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null) r = r.getCause();
        return r.getMessage();
    }
}
