package com.shopsystem.backend.dto;

import lombok.Data;

/** POST /api/v1/stores/{storeId}/checks/{checkId}/refunds のリクエストボディ（FR-G10）。 */
@Data
public class RefundRequest {
    /** 対象の決済（任意。特定しない場合は会計全体に対する返金として記録）。 */
    private Long paymentId;
    private int amountJpy;
    private String reason;
    private String reasonNote;
}
