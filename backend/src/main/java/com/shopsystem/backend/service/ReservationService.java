package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.ReservationRequest;
import com.shopsystem.backend.dto.ReservationResponse;
import com.shopsystem.backend.dto.ReservationStatusRequest;
import com.shopsystem.backend.entity.Reservation;
import com.shopsystem.backend.entity.Store;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.repository.ReservationRepository;
import com.shopsystem.backend.repository.UserRepository;
import com.shopsystem.backend.web.TenantContext;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 予約台帳（FR-C01・C02・C09）。電話予約・当日ウォークインをスタッフが登録・変更・キャンセルする。
 * Web予約フォーム（FR-C03〜C06）・前日リマインド（FR-C08）は別途対応する。
 * 状態遷移は03_domain_model.md §4.1のとおり。SEATED／DONEは卓割当（FR-E）と連動するため
 * この画面では扱わない（table_session側の実装時に合わせて遷移させる）。
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final Set<String> STAFF_CHANNELS = Set.of("PHONE", "WALK_IN");
    private static final Set<String> EDITABLE_STATUSES = Set.of("REQUESTED", "CONFIRMED");

    /** 現在のstatus → 遷移可能なstatus（03_domain_model.md §4.1のうち、この画面で扱う範囲）。 */
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "REQUESTED", Set.of("CONFIRMED", "CANCELLED"),
            "CONFIRMED", Set.of("CANCELLED", "NO_SHOW"));

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final MessageSource messageSource;
    private final StoreAccessGuard accessGuard;

    /**
     * 予約の一覧（FR-C02）。{@code storeId} を指定すればその店舗のみ、省略時は横断表示：
     * 経営管理者は自テナント全店舗、店長・ホールは自分が所属する店舗（複数可）を横断して返す
     * （`AuditLogService#search` と同じ考え方）。
     */
    @Transactional(readOnly = true)
    public List<ReservationResponse> list(Long storeId, LocalDateTime from, LocalDateTime to) {
        TenantContext.Data ctx = TenantContext.get();
        boolean isOwner = "OWNER".equals(ctx.role());
        boolean isManagerOrHall = "MANAGER".equals(ctx.role()) || "HALL".equals(ctx.role());
        if (!isOwner && !isManagerOrHall) {
            throw accessGuard.forbidden();
        }

        List<Reservation> reservations;
        if (storeId != null) {
            accessGuard.requireStoreInTenant(storeId);
            accessGuard.requireCanManageReservations(storeId);
            reservations = reservationRepository
                    .findAllByStore_IdAndReservedAtGreaterThanEqualAndReservedAtLessThanOrderByReservedAt(
                            storeId, from, to);
        } else if (isOwner) {
            reservations = reservationRepository
                    .findAllByStore_Company_IdAndReservedAtGreaterThanEqualAndReservedAtLessThanOrderByReservedAt(
                            ctx.companyId(), from, to);
        } else {
            Set<Long> allowedStoreIds = ctx.storeIds();
            if (allowedStoreIds.isEmpty()) {
                return List.of();
            }
            reservations = reservationRepository
                    .findAllByStore_IdInAndReservedAtGreaterThanEqualAndReservedAtLessThanOrderByReservedAt(
                            allowedStoreIds, from, to);
        }
        return reservations.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReservationResponse create(Long storeId, ReservationRequest req) {
        Store store = accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanManageReservations(storeId);

        String channel = req.getChannel();
        if (channel == null || !STAFF_CHANNELS.contains(channel)) {
            throw new BusinessException(
                    List.of(err("reservation.error.channel.invalid", "channel")));
        }
        validateDetails(req, channel);

        TenantContext.Data ctx = TenantContext.get();
        String actor = currentActor(ctx.userId());
        LocalDateTime now = LocalDateTime.now();

        Reservation reservation = new Reservation();
        reservation.setStore(store);
        reservation.setCompanyCode(ctx.companyCode());
        applyDetails(reservation, req);
        reservation.setChannel(channel);
        // 電話予約・当日ウォークインは登録＝店舗が把握済みのため、即座にCONFIRMEDで開始する
        // （03_domain_model.md §4.1「[*] --> CONFIRMED : 電話予約」）。
        reservation.setStatus("CONFIRMED");
        reservation.setConfirmedBy(actor);
        reservation.setConfirmedAt(now);

        reservation = reservationRepository.save(reservation);
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse update(Long storeId, Long reservationId, ReservationRequest req) {
        accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanManageReservations(storeId);

        Reservation reservation = reservationRepository.findByIdAndStore_Id(reservationId, storeId)
                .orElseThrow(accessGuard::notFound);

        if (!EDITABLE_STATUSES.contains(reservation.getStatus())) {
            throw new BusinessException(
                    List.of(err("reservation.error.status.not-editable", null)));
        }

        validateDetails(req, reservation.getChannel());
        applyDetails(reservation, req);
        reservationRepository.save(reservation);
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse updateStatus(Long storeId, Long reservationId, ReservationStatusRequest req) {
        accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanManageReservations(storeId);

        Reservation reservation = reservationRepository.findByIdAndStore_Id(reservationId, storeId)
                .orElseThrow(accessGuard::notFound);

        String from = reservation.getStatus();
        String to = req.getStatus();
        Set<String> allowedNext = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (to == null || !allowedNext.contains(to)) {
            throw new BusinessException(
                    List.of(err("reservation.error.status.invalid-transition", "status")));
        }

        if ("CANCELLED".equals(to)) {
            String reason = trimToNull(req.getCancelledReason());
            if (reason == null) {
                throw new BusinessException(
                        List.of(err("reservation.error.cancelled-reason.required", "cancelledReason")));
            }
            reservation.setCancelledReason(reason);
        }

        if ("CONFIRMED".equals(to)) {
            TenantContext.Data ctx = TenantContext.get();
            reservation.setConfirmedBy(currentActor(ctx.userId()));
            reservation.setConfirmedAt(LocalDateTime.now());
        }

        reservation.setStatus(to);
        reservationRepository.save(reservation);
        return toResponse(reservation);
    }

    private void validateDetails(ReservationRequest req, String channel) {
        List<ErrorItem> errors = new ArrayList<>();
        if (req.getReservedAt() == null) {
            errors.add(err("reservation.error.reserved-at.required", "reservedAt"));
        }
        if (req.getPartySize() <= 0) {
            errors.add(err("reservation.error.party-size.invalid", "partySize"));
        }
        if (trimToNull(req.getGuestName()) == null) {
            errors.add(err("reservation.error.guest-name.required", "guestName"));
        }
        // 電話予約は、折り返し連絡できるよう電話番号を必須にする（ウォークインは任意のまま）。
        if ("PHONE".equals(channel) && trimToNull(req.getGuestPhone()) == null) {
            errors.add(err("reservation.error.guest-phone.required", "guestPhone"));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }
    }

    private void applyDetails(Reservation reservation, ReservationRequest req) {
        reservation.setReservedAt(req.getReservedAt());
        reservation.setPartySize(req.getPartySize());
        reservation.setGuestName(trimToNull(req.getGuestName()));
        reservation.setGuestPhone(trimToNull(req.getGuestPhone()));
        reservation.setGuestEmail(trimToNull(req.getGuestEmail()));
        reservation.setRequestNote(trimToNull(req.getRequestNote()));
    }

    private String currentActor(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getEmail())
                .orElse("user:" + userId);
    }

    private ReservationResponse toResponse(Reservation r) {
        return new ReservationResponse(
                r.getId(), r.getStore().getId(), r.getStore().getName(), r.getReservedAt(), r.getPartySize(),
                r.getGuestName(), r.getGuestPhone(), r.getGuestEmail(), r.getRequestNote(), r.getChannel(),
                r.getStatus(), r.getConfirmedBy(), r.getConfirmedAt(), r.getCancelledReason());
    }

    private ErrorItem err(String code, String field) {
        Locale locale = LocaleContextHolder.getLocale();
        return new ErrorItem(
                messageSource.getMessage(code, null, locale), field == null ? List.of() : List.of(field));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
