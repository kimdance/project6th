package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.PaymentMethodResponse;
import com.shopsystem.backend.dto.PaymentMethodUpdateRequest;
import com.shopsystem.backend.service.PaymentMethodService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 決済手段の有効化・接続情報の管理（FR-B04・FR-B05。04_architecture.md §6.3）。 */
@RestController
@RequestMapping("/api/v1/stores/{storeId}/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    @GetMapping
    public List<PaymentMethodResponse> list(@PathVariable Long storeId) {
        return paymentMethodService.list(storeId);
    }

    @PutMapping("/{methodType}")
    public PaymentMethodResponse update(
            @PathVariable Long storeId, @PathVariable String methodType,
            @RequestBody PaymentMethodUpdateRequest body) {
        return paymentMethodService.update(storeId, methodType, body);
    }
}
