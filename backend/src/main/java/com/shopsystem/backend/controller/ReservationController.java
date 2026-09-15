package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.ReservationRequest;
import com.shopsystem.backend.dto.ReservationResponse;
import com.shopsystem.backend.dto.ReservationStatusRequest;
import com.shopsystem.backend.service.ReservationService;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 予約台帳の閲覧・登録・変更・キャンセル（FR-C01・C02・C09。04_architecture.md §6.3）。
 * ログイン済み前提。Web予約フォーム（認証不要・FR-C03）は別コントローラで対応する。
 * 一覧（GET）は店舗を横断できるよう {@code /api/v1/reservations} に置く（`AuditLogController` と
 * 同じ考え方）。登録・変更は対象店舗が常に1つに定まるため、従来どおり店舗配下に置く。
 */
@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /**
     * 予約の一覧（日表示／週表示。FR-C02）。{@code storeId} 省略時は経営管理者は全店、
     * 店長・ホールは自分の所属店舗を横断して返す。{@code date} 起点で {@code days} 日分
     * （既定は1日）を返す。例：週表示は {@code days=7}。
     */
    @GetMapping("/api/v1/reservations")
    public List<ReservationResponse> list(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "1") int days) {
        LocalDate from = date != null ? date : LocalDate.now();
        return reservationService.list(storeId, from.atStartOfDay(), from.plusDays(days).atStartOfDay());
    }

    @PostMapping("/api/v1/stores/{storeId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@PathVariable Long storeId, @RequestBody ReservationRequest body) {
        return reservationService.create(storeId, body);
    }

    @PatchMapping("/api/v1/stores/{storeId}/reservations/{reservationId}")
    public ReservationResponse update(
            @PathVariable Long storeId, @PathVariable Long reservationId, @RequestBody ReservationRequest body) {
        return reservationService.update(storeId, reservationId, body);
    }

    @PatchMapping("/api/v1/stores/{storeId}/reservations/{reservationId}/status")
    public ReservationResponse updateStatus(
            @PathVariable Long storeId, @PathVariable Long reservationId,
            @RequestBody ReservationStatusRequest body) {
        return reservationService.updateStatus(storeId, reservationId, body);
    }
}
