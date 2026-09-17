package com.shopsystem.backend.exception;

import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.ErrorResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

/**
 * 業務例外を統一フォーマット（ErrorResponse / ErrorItem。04_architecture.md §6.2）へ変換する。
 * フレームワーク由来の例外（不正JSON→400、未定義パス→404 など）は Spring Boot の既定処理に委ねる
 * （ただしアップロードサイズ超過は利用者への案内メッセージが必要なため例外的にここで扱う）。
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

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

    /** 権限不足・合言葉不一致 → 403。 */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        ErrorItem item = new ErrorItem(e.getMessage(), List.of());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(List.of(item)));
    }

    /** ログイン失敗・トークン不正 → 401。 */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException e) {
        ErrorItem item = new ErrorItem(e.getMessage(), List.of());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(List.of(item)));
    }

    /** サブドメインに対応するテナントが存在しない → 404（存在有無は漏らさない）。 */
    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTenantNotFound(TenantNotFoundException e) {
        ErrorItem item = new ErrorItem(e.getMessage(), List.of());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(List.of(item)));
    }

    /** テナント外・存在しない業務リソース → 404（存在有無は漏らさない）。 */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        ErrorItem item = new ErrorItem(e.getMessage(), List.of());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(List.of(item)));
    }

    /**
     * アップロードファイルが {@code spring.servlet.multipart.max-file-size} を超えた → 400
     * （メニュー写真アップロード。FR-D01）。リクエスト解析の時点で発生するため
     * {@code FileStorageService} 側では検知できず、ここで一括して案内メッセージへ変換する。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        String message = messageSource.getMessage("menu.error.photo.too-large", null, LocaleContextHolder.getLocale());
        ErrorItem item = new ErrorItem(message, List.of("file"));
        return ResponseEntity.badRequest().body(new ErrorResponse(List.of(item)));
    }
}
