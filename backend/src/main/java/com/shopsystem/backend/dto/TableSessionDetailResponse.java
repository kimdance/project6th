package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** GET /api/v1/stores/{storeId}/table-sessions/{sessionId} のレスポンス（注文入力画面用）。 */
@Data
@AllArgsConstructor
public class TableSessionDetailResponse {
    private TableSessionResponse session;
    private List<OrderLineResponse> lines;
}
