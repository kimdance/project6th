package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Web予約申込のレスポンス（FR-C03・C06）。スタッフの識別情報（confirmedBy等）は内部情報のため含めない。
 */
@Data
@AllArgsConstructor
public class PublicReservationResponse {
    private Long id;
    private LocalDateTime reservedAt;
    private int partySize;
    private String guestName;
    /** REQUESTED（承認待ち）／CONFIRMED（確定） */
    private String status;
}
