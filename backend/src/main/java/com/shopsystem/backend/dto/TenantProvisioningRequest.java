package com.shopsystem.backend.dto;

import lombok.Data;

/**
 * テナント作成リクエスト（フェーズ1は運営者専用。04_architecture.md §6.1）。
 * company と最初の users（OWNER）を作成する。
 */
@Data
public class TenantProvisioningRequest {
    private String companyCode;
    private String companyName;
    private String ownerName;
    private String ownerEmail;
    private String password;
}
