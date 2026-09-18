package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/** 注文明細のレスポンス（FR-E02・E03・E03b・E03c・E07）。 */
@Data
@AllArgsConstructor
public class OrderLineResponse {
    private Long id;
    private Long orderId;
    private Long menuItemId;
    private String itemNameSnap;
    private int unitPriceSnapJpy;
    private String taxCategorySnap;
    private int quantity;
    private String note;
    /** PENDING / PREPARING / SERVED / CANCELLED / REJECTED */
    private String serveStatus;
    private LocalDateTime registeredAt;
    private String registeredBy;
    private LocalDateTime servedAt;
    private LocalDateTime cancelledAt;
    private String cancelledBy;
    private String cancelReason;
    private Boolean cancelChargeable;
    private Boolean wasCooked;
    private Long remakeOfLineId;
}
