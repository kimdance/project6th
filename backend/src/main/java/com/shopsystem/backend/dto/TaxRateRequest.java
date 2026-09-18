package com.shopsystem.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** POST /api/v1/stores/{storeId}/tax-rates のリクエストボディ（FR-B03）。 */
@Data
public class TaxRateRequest {
    /** STANDARD_10（標準税率）／REDUCED_8（軽減税率） */
    private String taxCategory;
    private BigDecimal ratePercent;
    private LocalDate effectiveFrom;
}
