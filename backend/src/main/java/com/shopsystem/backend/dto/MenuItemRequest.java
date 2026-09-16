package com.shopsystem.backend.dto;

import lombok.Data;

import java.time.LocalTime;

/** POST/PUT /api/v1/stores/{storeId}/menu-items[/{itemId}] のリクエストボディ（FR-D01）。 */
@Data
public class MenuItemRequest {
    private Long categoryId;
    private String name;
    private String description;
    private int priceJpy;
    /** STANDARD_10 / REDUCED_8 */
    private String taxCategory;
    /** COOK / NO_COOK */
    private String prepType = "COOK";
    private String photoUrl;
    private LocalTime serveTimeFrom;
    private LocalTime serveTimeTo;
    private int displayOrder;
    private boolean active = true;
}
