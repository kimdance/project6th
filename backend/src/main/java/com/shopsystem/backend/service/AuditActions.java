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
    public static final String TABLE_CHANGE = "TABLE_CHANGE";
    public static final String BUSINESS_DAY_CHANGE = "BUSINESS_DAY_CHANGE";
    public static final String RESERVATION_CHANGE = "RESERVATION_CHANGE";
    public static final String RESERVATION_CANCEL = "RESERVATION_CANCEL";
    public static final String MENU_CHANGE = "MENU_CHANGE";
    public static final String ORDER_LINE_CANCEL_AFTER_SERVE = "ORDER_LINE_CANCEL_AFTER_SERVE";
    public static final String CHECK_FINALIZE = "CHECK_FINALIZE";
    public static final String CHECK_VOID = "CHECK_VOID";
    public static final String CHECK_REFUND = "CHECK_REFUND";
    public static final String CHECK_DISCOUNT = "CHECK_DISCOUNT";

    private AuditActions() {
    }
}
