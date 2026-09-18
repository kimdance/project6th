package com.shopsystem.backend.dto;

import lombok.Data;

/** {@link SubmitOrderRequest} 配下の1明細（FR-E02）。 */
@Data
public class OrderLineItemRequest {
    private Long menuItemId;
    private int quantity;
    private String note;
}
