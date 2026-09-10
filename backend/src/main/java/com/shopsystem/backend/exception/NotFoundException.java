package com.shopsystem.backend.exception;

/**
 * テナント外・存在しないリソースへのアクセス。HTTP 404 に対応する。
 * 存在有無を漏らさないため、権限不足の 403 と使い分ける（04_architecture.md §6.2）。
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
