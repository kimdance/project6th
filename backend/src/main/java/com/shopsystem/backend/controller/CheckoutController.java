package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.AddPaymentRequest;
import com.shopsystem.backend.dto.ApplyDiscountRequest;
import com.shopsystem.backend.dto.CheckResponse;
import com.shopsystem.backend.dto.CreateCheckRequest;
import com.shopsystem.backend.dto.RefundRequest;
import com.shopsystem.backend.service.CheckoutService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 会計（チェック）の作成・値引き・決済・取消・返金（FR-G01〜G05・G06・G07・G07b・G10・G11。04_architecture.md §6.3）。 */
@RestController
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @GetMapping("/api/v1/stores/{storeId}/table-sessions/{sessionId}/checks")
    public List<CheckResponse> listChecks(@PathVariable Long storeId, @PathVariable Long sessionId) {
        return checkoutService.listChecks(storeId, sessionId);
    }

    @PostMapping("/api/v1/stores/{storeId}/table-sessions/{sessionId}/checks")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckResponse createCheck(
            @PathVariable Long storeId, @PathVariable Long sessionId, @RequestBody CreateCheckRequest body) {
        return checkoutService.createCheck(storeId, sessionId, body);
    }

    @GetMapping("/api/v1/stores/{storeId}/checks/{checkId}")
    public CheckResponse getCheck(@PathVariable Long storeId, @PathVariable Long checkId) {
        return checkoutService.getCheck(storeId, checkId);
    }

    @PostMapping("/api/v1/stores/{storeId}/checks/{checkId}/discounts")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckResponse applyDiscount(
            @PathVariable Long storeId, @PathVariable Long checkId, @RequestBody ApplyDiscountRequest body) {
        return checkoutService.applyDiscount(storeId, checkId, body);
    }

    @PostMapping("/api/v1/stores/{storeId}/checks/{checkId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckResponse addPayment(
            @PathVariable Long storeId, @PathVariable Long checkId, @RequestBody AddPaymentRequest body) {
        return checkoutService.addPayment(storeId, checkId, body);
    }

    @PostMapping("/api/v1/stores/{storeId}/checks/{checkId}/void")
    public CheckResponse voidCheck(@PathVariable Long storeId, @PathVariable Long checkId) {
        return checkoutService.voidCheck(storeId, checkId);
    }

    @PostMapping("/api/v1/stores/{storeId}/checks/{checkId}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckResponse refund(
            @PathVariable Long storeId, @PathVariable Long checkId, @RequestBody RefundRequest body) {
        return checkoutService.refund(storeId, checkId, body);
    }
}
