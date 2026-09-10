package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** GET /api/v1/auth/tenant のレスポンス。ログイン画面表示用の会社情報。 */
@Data
@AllArgsConstructor
public class TenantInfoResponse {
    private String companyCode;
    private String companyName;
}
