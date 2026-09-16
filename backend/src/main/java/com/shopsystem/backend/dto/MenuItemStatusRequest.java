package com.shopsystem.backend.dto;

import lombok.Data;

/** PATCH /api/v1/stores/{storeId}/menu-items/{itemId}/sales-status のリクエストボディ（FR-D03）。 */
@Data
public class MenuItemStatusRequest {
    /** ON_SALE / SOLD_OUT / SUSPENDED */
    private String salesStatus;
}
