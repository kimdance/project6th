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
    /**
     * ON_SALE / SOLD_OUT / SUSPENDED。フルの編集画面（更新時のみ）で「保存する」と同時に
     * 販売状況もまとめて変更できるようにする（2026-09-19改訂）。新規登録時はこの値を使わず、
     * 従来どおりサーバー側で決める（{@link com.shopsystem.backend.service.MenuService#createItem}）。
     */
    private String salesStatus;
}
