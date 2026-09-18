package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 会計（チェック）のレスポンス（FR-G01〜G11）。 */
@Data
@AllArgsConstructor
public class CheckResponse {
    private Long id;
    private Long tableSessionId;
    private int seqInSession;
    /** OPEN / FINALIZED / VOIDED */
    private String status;
    private int subtotalJpy;
    private int discountTotalJpy;
    private int taxTotalJpy;
    private int totalJpy;
    private String splitType;
    private Integer splitCount;
    private LocalDate businessDate;
    private LocalDateTime finalizedAt;
    private String finalizedBy;
    private List<CheckLineResponse> lines;
    private List<CheckDiscountResponse> discounts;
    private List<CheckTaxLineResponse> taxLines;
    private List<PaymentResponse> payments;
    private List<RefundResponse> refunds;
    /** 入金済み合計（status=SUCCESSのpaymentの合計）。 */
    private int paidTotalJpy;
    /** 残額（totalJpy - paidTotalJpy。0以下で精算完了）。 */
    private int balanceJpy;
}
