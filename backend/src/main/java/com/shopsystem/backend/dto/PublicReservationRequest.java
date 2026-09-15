package com.shopsystem.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** POST /api/v1/public/stores/{storeId}/reservations のリクエストボディ（Web予約。FR-C03・C04）。 */
@Data
public class PublicReservationRequest {
    private LocalDateTime reservedAt;
    private int partySize;
    private String guestName;
    private String guestPhone;
    private String guestEmail;
    private String requestNote;
}
