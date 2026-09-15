package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.PublicReservationRequest;
import com.shopsystem.backend.dto.PublicReservationResponse;
import com.shopsystem.backend.dto.PublicStoreResponse;
import com.shopsystem.backend.service.WebReservationService;
import com.shopsystem.backend.web.TenantResolutionInterceptor;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * お客様向けWeb予約（認証不要。FR-C03〜C06。04_architecture.md §6.1 2026-09-15追補）。
 * テナントはHostヘッダのサブドメインから解決する（{@link TenantResolutionInterceptor}。
 * WebConfigで /api/v1/public/** に適用）。
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class WebReservationController {

    private final WebReservationService webReservationService;

    @GetMapping("/stores")
    public List<PublicStoreResponse> listStores(HttpServletRequest request) {
        Long companyId = (Long) request.getAttribute(TenantResolutionInterceptor.ATTR_COMPANY_ID);
        return webReservationService.listStores(companyId);
    }

    @PostMapping("/stores/{storeId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicReservationResponse create(
            HttpServletRequest request, @PathVariable Long storeId, @RequestBody PublicReservationRequest body) {
        Long companyId = (Long) request.getAttribute(TenantResolutionInterceptor.ATTR_COMPANY_ID);
        String companyCode = (String) request.getAttribute(TenantResolutionInterceptor.ATTR_COMPANY_CODE);
        return webReservationService.create(companyId, companyCode, storeId, body);
    }
}
