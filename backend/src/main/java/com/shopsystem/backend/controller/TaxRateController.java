package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.TaxRateRequest;
import com.shopsystem.backend.dto.TaxRateResponse;
import com.shopsystem.backend.service.TaxRateService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 税率（標準・軽減）の管理（FR-B03。04_architecture.md §6.3）。 */
@RestController
@RequestMapping("/api/v1/stores/{storeId}/tax-rates")
@RequiredArgsConstructor
public class TaxRateController {

    private final TaxRateService taxRateService;

    @GetMapping
    public List<TaxRateResponse> list(@PathVariable Long storeId) {
        return taxRateService.list(storeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaxRateResponse create(@PathVariable Long storeId, @RequestBody TaxRateRequest body) {
        return taxRateService.create(storeId, body);
    }

    @DeleteMapping("/{rateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long storeId, @PathVariable Long rateId) {
        taxRateService.delete(storeId, rateId);
    }
}
