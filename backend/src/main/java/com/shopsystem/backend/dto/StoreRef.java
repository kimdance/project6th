package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 店舗の簡易参照（id・店名のみ）。MeResponse・UserSummaryResponse で所属店舗一覧に使う。 */
@Data
@AllArgsConstructor
public class StoreRef {
    private Long id;
    private String name;
}
