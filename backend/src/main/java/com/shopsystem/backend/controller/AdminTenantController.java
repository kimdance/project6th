package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.TenantProvisioningRequest;
import com.shopsystem.backend.entity.Company;
import com.shopsystem.backend.security.OperatorTokenGuard;
import com.shopsystem.backend.service.TenantProvisioningService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 運営者によるテナント作成。フェーズ1では公開セルフサービス登録は行わず、
 * 運営者が合言葉（X-Operator-Token）付きでこの受付を呼ぶ（02_requirements.md §3.1、
 * 04_architecture.md §6.1）。運営者認証・運営者コンソールはフェーズ2。
 */
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
public class AdminTenantController {

    private final TenantProvisioningService tenantProvisioningService;
    private final OperatorTokenGuard operatorTokenGuard;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> create(
            @RequestHeader(value = OperatorTokenGuard.HEADER, required = false) String operatorToken,
            @RequestBody TenantProvisioningRequest request) {
        operatorTokenGuard.verify(operatorToken);
        Company company = tenantProvisioningService.provision(request);
        return Map.of("companyCode", company.getCompanyCode());
    }
}
