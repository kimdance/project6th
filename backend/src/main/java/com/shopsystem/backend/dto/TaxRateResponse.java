package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** GET /api/v1/stores/{storeId}/tax-rates の1件（FR-B03）。 */
@Data
@AllArgsConstructor
public class TaxRateResponse {
    private Long id;
    /** STANDARD_10（標準税率）／REDUCED_8（軽減税率） */
    private String taxCategory;
    private BigDecimal ratePercent;
    private LocalDate effectiveFrom;
    /** 今日時点で実際に適用されている税率か（一覧の強調表示用）。 */
    private boolean currentlyEffective;
}
