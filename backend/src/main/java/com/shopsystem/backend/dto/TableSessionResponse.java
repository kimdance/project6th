package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/** 卓セッション（来店）のレスポンス（FR-E01）。 */
@Data
@AllArgsConstructor
public class TableSessionResponse {
    private Long id;
    private Long diningTableId;
    private String tableNo;
    /** OPEN / BILLING / CLOSED */
    private String status;
    private int partySize;
    private LocalDateTime openedAt;
    private String openedBy;
    private Long reservationId;
}
