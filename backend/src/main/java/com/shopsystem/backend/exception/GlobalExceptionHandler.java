package com.shopsystem.backend.exception;

import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.ErrorResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * 業務例外を統一フォーマット（ErrorResponse / ErrorItem。04_architecture.md §6.2）へ変換する。
 * フレームワーク由来の例外（不正JSON→400、未定義パス→404 など）は Spring Boot の既定処理に委ねる。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 蓄積したフィールド単位のバリデーションエラー → 400。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getErrorItems()));
    }

    /** 一意制約の衝突 → 409。 */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
        ErrorItem item = new ErrorItem(e.getMessage(), List.of());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(List.of(item)));
    }
}
