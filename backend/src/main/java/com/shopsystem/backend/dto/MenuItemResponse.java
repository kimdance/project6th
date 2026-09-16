package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalTime;

/** メニュー項目のレスポンス（FR-D01・D03）。 */
@Data
@AllArgsConstructor
public class MenuItemResponse {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String description;
    private int priceJpy;
    private String taxCategory;
    private String prepType;
    private String photoUrl;
    private LocalTime serveTimeFrom;
    private LocalTime serveTimeTo;
    /** ON_SALE / SOLD_OUT / SUSPENDED */
    private String salesStatus;
    private int displayOrder;
    private boolean active;
}
