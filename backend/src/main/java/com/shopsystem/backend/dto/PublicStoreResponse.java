package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** GET /api/v1/public/stores のレスポンス（Web予約の店舗選択用。FR-C03）。 */
@Data
@AllArgsConstructor
public class PublicStoreResponse {
    private Long id;
    private String name;
}
