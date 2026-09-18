package com.shopsystem.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

/** POST /api/v1/stores/{storeId}/checks/{checkId}/discounts のリクエストボディ（FR-G02）。 */
@Data
public class ApplyDiscountRequest {
    /** AMOUNT / RATE / COUPON / ROUNDING */
    private String type;
    /** AMOUNT・COUPON・ROUNDINGは円額、RATEは0〜100のパーセント値。 */
    private BigDecimal value;
    private String reason;
}
