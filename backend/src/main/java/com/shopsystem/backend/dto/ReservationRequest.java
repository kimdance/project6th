package com.shopsystem.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * POST/PATCH /api/v1/stores/{storeId}/reservations[/{id}] のリクエストボディ（FR-C01）。
 * スタッフによる台帳登録・編集用。channel は作成時のみ有効（PHONE／WALK_IN。Web予約はFR-C03で別経路）。
 */
@Data
public class ReservationRequest {
    private LocalDateTime reservedAt;
    private int partySize;
    private String guestName;
    private String guestPhone;
    private String guestEmail;
    private String requestNote;
    /** PHONE（電話予約）／WALK_IN（当日ウォークイン）。作成時のみ参照する。 */
    private String channel;
}
