package com.shopsystem.backend.exception;

/**
 * ログイン失敗・トークン不正など、認証そのものが成立しない場合。HTTP 401 に対応する。
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
