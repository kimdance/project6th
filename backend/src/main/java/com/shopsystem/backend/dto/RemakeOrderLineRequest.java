package com.shopsystem.backend.dto;

import lombok.Data;

/**
 * POST /api/v1/stores/{storeId}/order-lines/{lineId}/remake のリクエストボディ（FR-E03c）。
 * quantity を0以下のまま送るなど未指定の場合は元明細の数量・メモを引き継ぐ。
 */
@Data
public class RemakeOrderLineRequest {
    private Integer quantity;
    private String note;
}
