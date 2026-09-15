package com.shopsystem.backend.exception;

/**
 * 一意制約の衝突（例：サインアップ時の company_code 重複）。HTTP 409 に対応する。
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
