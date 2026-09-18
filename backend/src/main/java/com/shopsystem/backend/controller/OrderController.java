package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.CancelOrderLineRequest;
import com.shopsystem.backend.dto.OrderLineResponse;
import com.shopsystem.backend.dto.RemakeOrderLineRequest;
import com.shopsystem.backend.dto.SubmitOrderRequest;
import com.shopsystem.backend.dto.TableSessionDetailResponse;
import com.shopsystem.backend.dto.UpdateOrderLineRequest;
import com.shopsystem.backend.service.OrderService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 注文の入力・数量変更・取消・作り直し・提供済み記録（FR-E02・E03・E03b・E03c・E07。
 * 04_architecture.md §6.3）。
 */
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/api/v1/stores/{storeId}/table-sessions/{sessionId}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public TableSessionDetailResponse submit(
            @PathVariable Long storeId, @PathVariable Long sessionId, @RequestBody SubmitOrderRequest body) {
        return orderService.submit(storeId, sessionId, body);
    }

    @PatchMapping("/api/v1/stores/{storeId}/order-lines/{lineId}")
    public OrderLineResponse update(
            @PathVariable Long storeId, @PathVariable Long lineId, @RequestBody UpdateOrderLineRequest body) {
        return orderService.updateLine(storeId, lineId, body);
    }

    @PatchMapping("/api/v1/stores/{storeId}/order-lines/{lineId}/cancel")
    public OrderLineResponse cancel(
            @PathVariable Long storeId, @PathVariable Long lineId, @RequestBody CancelOrderLineRequest body) {
        return orderService.cancelLine(storeId, lineId, body);
    }

    @PostMapping("/api/v1/stores/{storeId}/order-lines/{lineId}/remake")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderLineResponse remake(
            @PathVariable Long storeId, @PathVariable Long lineId, @RequestBody RemakeOrderLineRequest body) {
        return orderService.remakeLine(storeId, lineId, body);
    }

    @PatchMapping("/api/v1/stores/{storeId}/order-lines/{lineId}/serve")
    public OrderLineResponse serve(@PathVariable Long storeId, @PathVariable Long lineId) {
        return orderService.markServed(storeId, lineId);
    }
}
