package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.BusinessDayExceptionRequest;
import com.shopsystem.backend.dto.BusinessDayExceptionResponse;
import com.shopsystem.backend.dto.BusinessDaysResponse;
import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.WeeklyBusinessDayItem;
import com.shopsystem.backend.entity.Store;
import com.shopsystem.backend.entity.StoreBusinessDay;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.exception.ConflictException;
import com.shopsystem.backend.repository.StoreBusinessDayRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 営業日・臨時休業の管理（FR-B07。04_architecture.md §4.4・§6.3）。
 * 曜日ごとの既定（weekly）と特定日の上書き（exceptions）を別々に管理する
 * （DB の ck_store_business_day_date_xor_weekday によりどちらか一方のみ）。
 */
@Service
@RequiredArgsConstructor
public class BusinessDayService {

    private final StoreBusinessDayRepository storeBusinessDayRepository;
    private final MessageSource messageSource;
    private final StoreAccessGuard accessGuard;
    private final AuditLogService auditLogService;

    public BusinessDaysResponse get(Long storeId) {
        accessGuard.requireCanView(storeId);

        List<WeeklyBusinessDayItem> weekly = storeBusinessDayRepository
                .findAllByStore_IdAndWeekdayIsNotNull(storeId).stream()
                .sorted((a, b) -> Integer.compare(a.getWeekday(), b.getWeekday()))
                .map(this::toWeeklyItem)
                .toList();

        List<BusinessDayExceptionResponse> exceptions = storeBusinessDayRepository
                .findAllByStore_IdAndBusinessDateIsNotNullOrderByBusinessDate(storeId).stream()
                .map(this::toExceptionResponse)
                .toList();

        return new BusinessDaysResponse(weekly, exceptions);
    }

    /** 送られてきた曜日の集合で全置換する（含まれない曜日の既存設定は削除する）。 */
    @Transactional
    public List<WeeklyBusinessDayItem> replaceWeekly(Long storeId, List<WeeklyBusinessDayItem> items) {
        Store store = accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanEdit(storeId);

        List<ErrorItem> errors = new ArrayList<>();
        Set<Short> seen = new HashSet<>();
        for (WeeklyBusinessDayItem item : items) {
            if (item.getWeekday() == null || item.getWeekday() < 0 || item.getWeekday() > 6) {
                errors.add(err("business-day.error.weekday.invalid", "weekday"));
            } else if (!seen.add(item.getWeekday().shortValue())) {
                errors.add(err("business-day.error.weekday.duplicate", "weekday"));
            }
            if (item.getReservationCapacity() != null && item.getReservationCapacity() < 0) {
                errors.add(err("business-day.error.reservation-capacity.invalid", "reservationCapacity"));
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }

        List<StoreBusinessDay> existing = storeBusinessDayRepository.findAllByStore_IdAndWeekdayIsNotNull(storeId);
        for (StoreBusinessDay row : existing) {
            if (!seen.contains(row.getWeekday())) {
                storeBusinessDayRepository.delete(row);
            }
        }

        List<WeeklyBusinessDayItem> result = new ArrayList<>();
        for (WeeklyBusinessDayItem item : items) {
            Short weekday = item.getWeekday().shortValue();
            StoreBusinessDay row = storeBusinessDayRepository.findByStore_IdAndWeekday(storeId, weekday)
                    .orElseGet(() -> {
                        StoreBusinessDay r = new StoreBusinessDay();
                        r.setStore(store);
                        r.setWeekday(weekday);
                        return r;
                    });
            row.setOpen(item.isOpen());
            row.setOpenTime(item.getOpenTime());
            row.setCloseTime(item.getCloseTime());
            row.setReservationCapacity(item.getReservationCapacity());
            row = storeBusinessDayRepository.save(row);
            result.add(toWeeklyItem(row));
        }

        auditLogService.recordForCurrentUser(AuditActions.BUSINESS_DAY_CHANGE, storeId, "BUSINESS_DAY", storeId,
                null, summarizeWeekly(result));

        return result;
    }

    @Transactional
    public BusinessDayExceptionResponse addException(Long storeId, BusinessDayExceptionRequest req) {
        Store store = accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanEdit(storeId);

        List<ErrorItem> errors = new ArrayList<>();
        if (req.getBusinessDate() == null) {
            errors.add(err("business-day.error.business-date.required", "businessDate"));
        } else if (storeBusinessDayRepository.existsByStore_IdAndBusinessDate(storeId, req.getBusinessDate())) {
            throw new ConflictException(
                    messageSource.getMessage("business-day.error.business-date.duplicate", null,
                            LocaleContextHolder.getLocale()));
        }
        if (req.getReservationCapacity() != null && req.getReservationCapacity() < 0) {
            errors.add(err("business-day.error.reservation-capacity.invalid", "reservationCapacity"));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }

        StoreBusinessDay row = new StoreBusinessDay();
        row.setStore(store);
        row.setBusinessDate(req.getBusinessDate());
        row.setOpen(req.isOpen());
        row.setOpenTime(req.getOpenTime());
        row.setCloseTime(req.getCloseTime());
        row.setReservationCapacity(req.getReservationCapacity());
        row = storeBusinessDayRepository.save(row);

        auditLogService.recordForCurrentUser(AuditActions.BUSINESS_DAY_CHANGE, storeId, "BUSINESS_DAY", row.getId(),
                null, summarizeException(row));

        return toExceptionResponse(row);
    }

    @Transactional
    public void deleteException(Long storeId, Long exceptionId) {
        accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanEdit(storeId);

        StoreBusinessDay row = storeBusinessDayRepository.findByIdAndStore_Id(exceptionId, storeId)
                .filter(r -> r.getBusinessDate() != null)
                .orElseThrow(accessGuard::notFound);
        String beforeSummary = summarizeException(row);
        storeBusinessDayRepository.delete(row);

        auditLogService.recordForCurrentUser(AuditActions.BUSINESS_DAY_CHANGE, storeId, "BUSINESS_DAY", exceptionId,
                beforeSummary, null);
    }

    private String summarizeWeekly(List<WeeklyBusinessDayItem> items) {
        return items.stream()
                .map(i -> "weekday" + i.getWeekday() + "=" + (i.isOpen() ? "OPEN" : "CLOSED")
                        + (i.getReservationCapacity() != null ? ",cap=" + i.getReservationCapacity() : ""))
                .reduce((a, b) -> a + "; " + b)
                .orElse("(empty)");
    }

    private String summarizeException(StoreBusinessDay row) {
        return "businessDate=" + row.getBusinessDate() + ", open=" + row.isOpen()
                + ", capacity=" + row.getReservationCapacity();
    }

    private WeeklyBusinessDayItem toWeeklyItem(StoreBusinessDay row) {
        return new WeeklyBusinessDayItem(
                row.getWeekday() == null ? null : row.getWeekday().intValue(),
                row.isOpen(), row.getOpenTime(), row.getCloseTime(), row.getReservationCapacity());
    }

    private BusinessDayExceptionResponse toExceptionResponse(StoreBusinessDay row) {
        return new BusinessDayExceptionResponse(
                row.getId(), row.getBusinessDate(), row.isOpen(), row.getOpenTime(), row.getCloseTime(),
                row.getReservationCapacity());
    }

    private ErrorItem err(String code, String field) {
        Locale locale = LocaleContextHolder.getLocale();
        return new ErrorItem(messageSource.getMessage(code, null, locale), List.of(field));
    }
}
