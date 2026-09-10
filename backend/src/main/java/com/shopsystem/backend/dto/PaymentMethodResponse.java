package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 決済手段のレスポンス（FR-B04・FR-B05）。接続情報は暗号化して保存し平文を一切返さないため、
 * 設定済みかどうかだけを {@code hasCredential} で示す。
 */
@Data
@AllArgsConstructor
public class PaymentMethodResponse {
    private String methodType;
    private boolean enabled;
    private String displayName;
    private String note;
    private boolean hasCredential;
}
