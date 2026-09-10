package com.shopsystem.backend.dto;

import lombok.Data;

/** POST/PUT /api/v1/stores/{storeId}/tables[/{tableId}] のリクエストボディ（FR-B02）。 */
@Data
public class TableRequest {
    private String tableNo;
    private int seatCount;
    private String area;
    private boolean active = true;
}
