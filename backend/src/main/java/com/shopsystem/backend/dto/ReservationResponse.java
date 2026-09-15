package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/** 予約のレスポンス（FR-C01〜C09）。 */
@Data
@AllArgsConstructor
public class ReservationResponse {
    private Long id;
    private Long storeId;
    private String storeName;
    private LocalDateTime reservedAt;
    private int partySize;
    private String guestName;
    private String guestPhone;
    private String guestEmail;
    private String requestNote;
    /** WEB／PHONE／WALK_IN */
    private String channel;
    /** REQUESTED／CONFIRMED／SEATED／DONE／CANCELLED／NO_SHOW */
    private String status;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private String cancelledReason;
}
