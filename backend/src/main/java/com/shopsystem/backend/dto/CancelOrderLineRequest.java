package com.shopsystem.backend.dto;

import lombok.Data;

/**
 * PATCH /api/v1/stores/{storeId}/order-lines/{lineId}/cancel のリクエストボディ（FR-E03・E03b）。
 * chargeable を省略（null）した場合、店舗設定の既定（客都合／店都合）から自動算出する。
 */
@Data
public class CancelOrderLineRequest {
    /** ORDER_MISTAKE / QUALITY / DELAY / WRONG_SERVE / SOLD_OUT / CUSTOMER / OTHER */
    private String reason;
    /** 調理済み（廃棄ロス）だったか。未調理ならfalse。 */
    private Boolean wasCooked;
    /** 請求対象にするかの上書き。省略時は店舗設定の既定から算出。 */
    private Boolean chargeable;
}
