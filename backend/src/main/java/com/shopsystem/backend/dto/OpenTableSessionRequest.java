package com.shopsystem.backend.dto;

import lombok.Data;

/** POST /api/v1/stores/{storeId}/table-sessions のリクエストボディ（FR-E01・FR-C07）。 */
@Data
public class OpenTableSessionRequest {
    private Long diningTableId;
    private int partySize;
    /** 予約からの来店割当の場合のみ指定（FR-C07）。対象予約はCONFIRMEDである必要がある。 */
    private Long reservationId;
}
