package com.shopsystem.backend.dto;

import lombok.Data;

/**
 * PATCH /api/v1/stores/{storeId}/order-lines/{lineId} のリクエストボディ（FR-E03の数量変更）。
 * 提供前（serveStatus=PENDING）の明細のみ変更できる。
 */
@Data
public class UpdateOrderLineRequest {
    private int quantity;
    private String note;
}
