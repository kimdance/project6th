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
}
