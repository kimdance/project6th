package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.OpenTableSessionRequest;
import com.shopsystem.backend.dto.TableSessionDetailResponse;
import com.shopsystem.backend.dto.TableSessionResponse;
import com.shopsystem.backend.service.OrderService;
import com.shopsystem.backend.service.TableSessionService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 卓セッション（来店）の管理（FR-E01・FR-C07。04_architecture.md §6.3）。 */
@RestController
@RequestMapping("/api/v1/stores/{storeId}/table-sessions")
@RequiredArgsConstructor
public class TableSessionController {

    private final TableSessionService tableSessionService;
    private final OrderService orderService;

    /** 営業中（OPEN／BILLING）の卓セッション一覧（卓ボード表示用）。 */
    @GetMapping
    public List<TableSessionResponse> listActive(@PathVariable Long storeId) {
        return tableSessionService.listActive(storeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TableSessionResponse open(@PathVariable Long storeId, @RequestBody OpenTableSessionRequest body) {
        return tableSessionService.open(storeId, body);
    }

    /** 卓セッションの詳細（現在の注文明細一覧。注文入力画面用）。 */
    @GetMapping("/{sessionId}")
    public TableSessionDetailResponse getDetail(@PathVariable Long storeId, @PathVariable Long sessionId) {
        return orderService.getSessionDetail(storeId, sessionId);
    }
}
