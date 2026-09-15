package com.shopsystem.backend.exception;

/**
 * サブドメインに対応する company が存在しない場合（www・apex・サブドメインなしを含む）。
 * テナントの存在有無を漏らさないため常に汎用メッセージのHTTP 404とする（04_architecture.md §6.1）。
 */
public class TenantNotFoundException extends RuntimeException {

    public TenantNotFoundException() {
        super("リクエストされたテナントは存在しません。");
    }
}
