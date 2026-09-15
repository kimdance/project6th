package com.shopsystem.backend.service;

import com.shopsystem.backend.config.PasswordResetProperties;
import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.PublicReservationRequest;
import com.shopsystem.backend.dto.PublicReservationResponse;
import com.shopsystem.backend.dto.PublicStoreResponse;
import com.shopsystem.backend.entity.Reservation;
import com.shopsystem.backend.entity.ReservationNotification;
import com.shopsystem.backend.entity.Store;
import com.shopsystem.backend.entity.StoreBusinessDay;
import com.shopsystem.backend.entity.StoreSetting;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.exception.NotFoundException;
import com.shopsystem.backend.repository.ReservationNotificationRepository;
import com.shopsystem.backend.repository.ReservationRepository;
import com.shopsystem.backend.repository.StoreBusinessDayRepository;
import com.shopsystem.backend.repository.StoreRepository;
import com.shopsystem.backend.repository.StoreSettingRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * お客様向けWeb予約フォーム（認証不要。FR-C03〜C06。04_architecture.md §6.1 2026-09-15追補）。
 * テナントはHostヘッダのサブドメインから{@link com.shopsystem.backend.web.TenantResolutionInterceptor}
 * が解決済み（company_id をリクエスト属性から受け取る）。スタッフ用の {@link ReservationService}
 * とは別サービス：公開APIとして返すフィールド・検証内容が異なるため分離する。
 */
@Service
@RequiredArgsConstructor
public class WebReservationService {

    private static final Set<String> CLOSED_STATUSES = Set.of("CANCELLED", "NO_SHOW");

    private final StoreRepository storeRepository;
    private final StoreSettingRepository storeSettingRepository;
    private final StoreBusinessDayRepository storeBusinessDayRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationNotificationRepository reservationNotificationRepository;
    private final JavaMailSender mailSender;
    private final PasswordResetProperties mailProperties;
    private final MessageSource messageSource;
    private final AuditLogService auditLogService;

    public List<PublicStoreResponse> listStores(Long companyId) {
        return storeRepository.findByCompany_IdAndActiveTrueOrderById(companyId).stream()
                .map(s -> new PublicStoreResponse(s.getId(), s.getName()))
                .toList();
    }

    @Transactional
    public PublicReservationResponse create(Long companyId, String companyCode, Long storeId,
            PublicReservationRequest req) {
        Store store = storeRepository.findByIdAndCompany_Id(storeId, companyId)
                .filter(Store::isActive)
                .orElseThrow(() -> new NotFoundException(
                        messageSource.getMessage("store.error.not-found", null, LocaleContextHolder.getLocale())));

        validate(store, req);

        StoreSetting setting = storeSettingRepository.findById(storeId).orElse(null);
        boolean instant = setting != null && "INSTANT".equals(setting.getWebReservationMode());

        Reservation reservation = new Reservation();
        reservation.setStore(store);
        reservation.setCompanyCode(companyCode);
        reservation.setReservedAt(req.getReservedAt());
        reservation.setPartySize(req.getPartySize());
        reservation.setGuestName(trimToNull(req.getGuestName()));
        reservation.setGuestPhone(trimToNull(req.getGuestPhone()));
        reservation.setGuestEmail(trimToNull(req.getGuestEmail()));
        reservation.setRequestNote(trimToNull(req.getRequestNote()));
        reservation.setChannel("WEB");
        if (instant) {
            reservation.setStatus("CONFIRMED");
            reservation.setConfirmedBy("system:web-instant");
            reservation.setConfirmedAt(LocalDateTime.now());
        } else {
            reservation.setStatus("REQUESTED");
        }
        reservation = reservationRepository.save(reservation);

        auditLogService.record(companyCode, storeId, "guest:web", AuditActions.RESERVATION_CHANGE,
                "RESERVATION", reservation.getId(), null,
                "reservedAt=" + reservation.getReservedAt() + ", partySize=" + reservation.getPartySize()
                        + ", guestName=" + reservation.getGuestName() + ", channel=WEB, status="
                        + reservation.getStatus());

        if (reservation.getGuestEmail() != null) {
            sendConfirmationEmail(reservation, store, instant);
        }

        return new PublicReservationResponse(
                reservation.getId(), reservation.getReservedAt(), reservation.getPartySize(),
                reservation.getGuestName(), reservation.getStatus());
    }

    /** FR-C04：店舗の営業日・受付時間・受付上限に基づく可否判定。 */
    private void validate(Store store, PublicReservationRequest req) {
        List<ErrorItem> errors = new ArrayList<>();
        if (req.getReservedAt() == null) {
            errors.add(err("reservation.error.reserved-at.required", "reservedAt"));
        } else if (req.getReservedAt().isBefore(LocalDateTime.now())) {
            errors.add(err("reservation.error.reserved-at.past", "reservedAt"));
        }
        if (req.getPartySize() <= 0) {
            errors.add(err("reservation.error.party-size.invalid", "partySize"));
        }
        if (trimToNull(req.getGuestName()) == null) {
            errors.add(err("reservation.error.guest-name.required", "guestName"));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }

        LocalDate date = req.getReservedAt().toLocalDate();
        StoreBusinessDay dayConfig = storeBusinessDayRepository
                .findByStore_IdAndBusinessDate(store.getId(), date)
                .orElseGet(() -> storeBusinessDayRepository
                        .findByStore_IdAndWeekday(store.getId(), appWeekday(date))
                        .orElse(null));
        // 営業日の設定が無い店舗は、フェーズ1では制限なし（常時受付可）として扱う。
        if (dayConfig == null) {
            return;
        }
        if (!dayConfig.isOpen()) {
            throw new BusinessException(List.of(err("reservation.error.store-closed", "reservedAt")));
        }
        LocalTime time = req.getReservedAt().toLocalTime();
        if (dayConfig.getOpenTime() != null && dayConfig.getCloseTime() != null
                && (time.isBefore(dayConfig.getOpenTime()) || time.isAfter(dayConfig.getCloseTime()))) {
            throw new BusinessException(List.of(err("reservation.error.outside-hours", "reservedAt")));
        }
        if (dayConfig.getReservationCapacity() != null) {
            int booked = reservationRepository
                    .findAllByStore_IdAndReservedAtGreaterThanEqualAndReservedAtLessThanOrderByReservedAt(
                            store.getId(), date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                    .stream()
                    .filter(r -> !CLOSED_STATUSES.contains(r.getStatus()))
                    .mapToInt(Reservation::getPartySize)
                    .sum();
            if (booked + req.getPartySize() > dayConfig.getReservationCapacity()) {
                throw new BusinessException(List.of(err("reservation.error.capacity-exceeded", "partySize")));
            }
        }
    }

    /** 03_domain_model.md／messages.properties の規約に合わせ 0=日曜〜6=土曜 に変換する。 */
    private static short appWeekday(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return (short) (dow.getValue() % 7);
    }

    /** 受付確認メール（FR-C05）。送信失敗は予約自体を失敗させず、通知記録にFAILEDとして残す。 */
    private void sendConfirmationEmail(Reservation reservation, Store store, boolean instant) {
        ReservationNotification notification = new ReservationNotification();
        notification.setReservation(reservation);
        notification.setType("CONFIRM");
        notification.setChannel("EMAIL");
        notification.setTo(reservation.getGuestEmail());

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailProperties.getMailFrom());
            message.setTo(reservation.getGuestEmail());
            message.setSubject("【" + store.getName() + "】ご予約を受け付けました");
            String statusLine = instant
                    ? "ご予約が確定しました。"
                    : "ご予約を承りました。店舗からの確定のご連絡をお待ちください。";
            message.setText(statusLine + "\n\n"
                    + "日時: " + reservation.getReservedAt() + "\n"
                    + "人数: " + reservation.getPartySize() + "名\n"
                    + "お名前: " + reservation.getGuestName() + "様\n\n"
                    + "このメールに心当たりがない場合は、破棄してください。");
            mailSender.send(message);
            notification.setStatus("SENT");
            notification.setSentAt(LocalDateTime.now());
        } catch (MailException e) {
            notification.setStatus("FAILED");
        }
        reservationNotificationRepository.save(notification);
    }

    private ErrorItem err(String code, String field) {
        Locale locale = LocaleContextHolder.getLocale();
        return new ErrorItem(messageSource.getMessage(code, null, locale), List.of(field));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
