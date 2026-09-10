package com.shopsystem.backend.dto;

import lombok.Data;

/** POST /api/v1/stores のリクエストボディ（FR-B01）。作成直後は既定値の店舗設定が1件あわせて作られる。 */
@Data
public class StoreCreateRequest {
    private String name;
    private String address;
    private String phone;
    private String businessHours;
    private int seatCount;
}
