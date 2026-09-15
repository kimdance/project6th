package com.shopsystem.backend.dto;

import lombok.Data;

/**
 * PUT /api/v1/stores/{storeId}/payment-methods/{methodType} のリクエストボディ（FR-B04・FR-B05）。
 * credential は書き込み専用：省略（null）なら既存の接続情報を保持し、空文字を送ると削除する。
 * 平文はレスポンスで返さない。
 */
@Data
public class PaymentMethodUpdateRequest {
    private boolean enabled;
    private String displayName;
    private String credential;
    private String note;
}
