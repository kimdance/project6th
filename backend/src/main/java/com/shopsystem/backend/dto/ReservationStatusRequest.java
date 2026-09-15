package com.shopsystem.backend.dto;

import lombok.Data;

/**
 * PATCH /api/v1/stores/{storeId}/reservations/{id}/status のリクエストボディ（FR-C09）。
 * 許可される遷移は 03_domain_model.md §4.1 のとおり：
 * REQUESTED→CONFIRMED／CANCELLED、CONFIRMED→CANCELLED／NO_SHOW。
 * SEATED／DONE は卓割当（FR-E、table_session）と連動するため、この画面では扱わない。
 */
@Data
public class ReservationStatusRequest {
    private String status;
    /** status=CANCELLED のとき必須。 */
    private String cancelledReason;
}
