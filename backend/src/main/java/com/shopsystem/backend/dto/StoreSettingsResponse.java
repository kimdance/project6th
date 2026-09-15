package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** GET/PUT /api/v1/stores/{storeId}/settings のレスポンス。 */
@Data
@AllArgsConstructor
public class StoreSettingsResponse {
    private Long storeId;
    private String name;
    private String address;
    private String phone;
    private String businessHours;
    private int seatCount;
    private String taxRounding;
    private boolean priceIncludesTax;
    private String invoiceRegNo;
    /** APPROVAL（承認制）／INSTANT（即時確定）。Web予約の確定方式（FR-B09・FR-C06）。 */
    private String webReservationMode;
    /** 客都合キャンセルを既定で請求するか（FR-B08）。 */
    private boolean cancelChargeDefaultCustomer;
    /** 店都合キャンセルを既定で請求するか（FR-B08）。 */
    private boolean cancelChargeDefaultStore;
}
