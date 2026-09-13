package com.shopsystem.backend.service;

/**
 * 監査ログの操作種別（action）定数（FR-J01。03_domain_model.md §2.8）。
 * まだ実装していない機能（メニュー価格変更・会計確定／取消／返金・日次締め・打刻の手修正・
 * データエクスポート等）の値は、その機能を実装する際にここへ追加して記録する。
 */
public final class AuditActions {

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String USER_REGISTER = "USER_REGISTER";
    public static final String PERMISSION_CHANGE = "PERMISSION_CHANGE";
    public static final String STORE_SETTING_CHANGE = "STORE_SETTING_CHANGE";
    public static final String PAYMENT_SETTING_CHANGE = "PAYMENT_SETTING_CHANGE";

    private AuditActions() {
    }
}
