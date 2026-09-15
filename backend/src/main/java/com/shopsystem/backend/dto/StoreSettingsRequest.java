package com.shopsystem.backend.dto;

import lombok.Data;

/**
 * GET/PUT /api/v1/stores/{storeId}/settings のリクエストボディ。
 * 今回の実装範囲は基本情報（FR-B01）と税金設定（FR-B03）まで。卓・決済手段・営業日は別途追加する。
 */
@Data
public class StoreSettingsRequest {
    private String name;
    private String address;
    private String phone;
    private String businessHours;
    private int seatCount;

    /** FLOOR / CEIL / ROUND */
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
