package com.shopsystem.backend.dto;

import lombok.Data;

/**
 * POST /api/v1/stores/{storeId}/checks/{checkId}/payments のリクエストボディ（FR-G03・G05・G06・G07・G07b）。
 * フェーズ1は手入力方式（決済代行との自動連携なし）のため、スタッフが確認した金額をそのまま記録する。
 */
@Data
public class AddPaymentRequest {
    /** CASH / PAYPAY / CREDIT_CARD / RAKUTEN_PAY */
    private String methodType;
    private int amountJpy;
    /** 現金のみ：預り金（釣り銭の自動計算に使う。省略時は amountJpy と同額＝ちょうど）。 */
    private Integer tenderedJpy;
}
