package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.StoreCreateRequest;
import com.shopsystem.backend.dto.StoreResponse;
import com.shopsystem.backend.dto.StoreSettingsRequest;
import com.shopsystem.backend.dto.StoreSettingsResponse;
import com.shopsystem.backend.service.StoreService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店舗の作成・設定編集（FR-B01・FR-B03。04_architecture.md §6.3）。
 * ログイン済み前提（{@code JwtAuthenticationInterceptor} が事前に認証・テナント文脈を確立する）。
 */
@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreResponse create(@RequestBody StoreCreateRequest body) {
        return storeService.create(body);
    }

    @GetMapping("/{storeId}/settings")
    public StoreSettingsResponse getSettings(@PathVariable Long storeId) {
        return storeService.getSettings(storeId);
    }

    @PutMapping("/{storeId}/settings")
    public StoreSettingsResponse updateSettings(@PathVariable Long storeId, @RequestBody StoreSettingsRequest body) {
        return storeService.updateSettings(storeId, body);
    }
}
