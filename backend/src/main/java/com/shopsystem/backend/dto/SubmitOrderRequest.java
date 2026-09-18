package com.shopsystem.backend.dto;

import lombok.Data;

import java.util.List;

/** POST /api/v1/stores/{storeId}/table-sessions/{sessionId}/orders のリクエストボディ（FR-E02）。 */
@Data
public class SubmitOrderRequest {
    private List<OrderLineItemRequest> lines;
}
