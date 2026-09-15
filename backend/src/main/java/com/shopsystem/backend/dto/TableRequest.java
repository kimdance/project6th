package com.shopsystem.backend.dto;

import lombok.Data;

/** POST/PUT /api/v1/stores/{storeId}/tables[/{tableId}] のリクエストボディ（FR-B02）。 */
@Data
public class TableRequest {
    private String tableNo;
    private int seatCount;
    /** COUNTER（カウンター席）／TABLE（テーブル席）。COUNTER 指定時は seatCount を1に強制する。 */
    private String seatType;
    private String area;
    private boolean active = true;
}
