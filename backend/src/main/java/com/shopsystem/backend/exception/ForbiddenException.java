package com.shopsystem.backend.exception;

/**
 * 権限不足・合言葉不一致など。HTTP 403 に対応する。
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
