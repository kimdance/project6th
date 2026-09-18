package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.StoreCreateRequest;
import com.shopsystem.backend.dto.StoreResponse;
import com.shopsystem.backend.dto.StoreSettingsRequest;
import com.shopsystem.backend.dto.StoreSettingsResponse;
import com.shopsystem.backend.entity.Store;
import com.shopsystem.backend.entity.StoreSetting;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.repository.CompanyRepository;
import com.shopsystem.backend.repository.StoreRepository;
import com.shopsystem.backend.repository.StoreSettingRepository;
import com.shopsystem.backend.web.TenantContext;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 店舗の作成・設定編集（FR-B01・FR-B03。04_architecture.md §6.3）。
 * 権限は `02_requirements.md` §3.2 の権限マトリクスに従う：作成はオーナーのみ、編集は
 * オーナー（全店）／店長（自店のみ）、それ以外のロールは編集不可。閲覧はオーナー（全店）／
 * 店長（自店のみ）／それ以外のロール（ホール・キッチン・バイト。テナント内であれば制限なし）
 * ができる（{@link StoreAccessGuard#requireCanView}）。
 */
@Service
@RequiredArgsConstructor
public class StoreService {

    private static final Pattern TAX_ROUNDING = Pattern.compile("^(FLOOR|CEIL|ROUND)$");
    private static final Pattern WEB_RESERVATION_MODE = Pattern.compile("^(APPROVAL|INSTANT)$");

    private final StoreRepository storeRepository;
    private final StoreSettingRepository storeSettingRepository;
    private final CompanyRepository companyRepository;
    private final MessageSource messageSource;
    private final StoreAccessGuard accessGuard;
    private final AuditLogService auditLogService;

    @Transactional
    public StoreResponse create(StoreCreateRequest req) {
        TenantContext.Data ctx = TenantContext.get();
        accessGuard.requireOwner();

        List<ErrorItem> errors = new ArrayList<>();
        String name = trimToNull(req.getName());
        if (name == null) {
            errors.add(err("store.error.name.required", "name"));
        }
        if (req.getSeatCount() < 0) {
            errors.add(err("store.error.seat-count.invalid", "seatCount"));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }

        Store store = new Store();
        store.setCompany(companyRepository.getReferenceById(ctx.companyId()));
        store.setName(name);
        store.setAddress(trimToNull(req.getAddress()));
        store.setPhone(trimToNull(req.getPhone()));
        store.setBusinessHours(trimToNull(req.getBusinessHours()));
        store.setSeatCount(req.getSeatCount());
        store = storeRepository.save(store);

        StoreSetting setting = new StoreSetting();
        setting.setStore(store);
        storeSettingRepository.save(setting);

        return toStoreResponse(store);
    }

    /**
     * 自テナントの店舗一覧（作成済みかどうかの確認用、店舗選択画面の選択肢）。
     * オーナー・ホール・キッチン・バイトは全店、店長は自分の所属店舗のみ（複数可）。
     */
    public List<StoreResponse> list() {
        TenantContext.Data ctx = TenantContext.get();
        List<Store> stores = storeRepository.findByCompany_IdOrderById(ctx.companyId());
        if ("MANAGER".equals(ctx.role())) {
            stores = stores.stream().filter(s -> ctx.storeIds().contains(s.getId())).toList();
        }
        return stores.stream().map(this::toStoreResponse).toList();
    }

    public StoreSettingsResponse getSettings(Long storeId) {
        Store store = accessGuard.requireCanView(storeId);
        StoreSetting setting = storeSettingRepository.findById(storeId).orElseThrow(accessGuard::notFound);
        return toSettingsResponse(store, setting);
    }

    @Transactional
    public StoreSettingsResponse updateSettings(Long storeId, StoreSettingsRequest req) {
        Store store = accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanEdit(storeId);
        StoreSetting existingSetting = storeSettingRepository.findById(storeId).orElseThrow(accessGuard::notFound);
        String beforeSummary = summarizeStoreSettings(store, existingSetting);

        List<ErrorItem> errors = new ArrayList<>();
        String name = trimToNull(req.getName());
        if (name == null) {
            errors.add(err("store.error.name.required", "name"));
        }
        if (req.getSeatCount() < 0) {
            errors.add(err("store.error.seat-count.invalid", "seatCount"));
        }
        String taxRounding = trimToNull(req.getTaxRounding());
        if (taxRounding == null || !TAX_ROUNDING.matcher(taxRounding.toUpperCase(Locale.ROOT)).matches()) {
            errors.add(err("store.error.tax-rounding.invalid", "taxRounding"));
        }
        String webReservationMode = trimToNull(req.getWebReservationMode());
        if (webReservationMode == null
                || !WEB_RESERVATION_MODE.matcher(webReservationMode.toUpperCase(Locale.ROOT)).matches()) {
            errors.add(err("store.error.web-reservation-mode.invalid", "webReservationMode"));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }

        store.setName(name);
        store.setAddress(trimToNull(req.getAddress()));
        store.setPhone(trimToNull(req.getPhone()));
        store.setBusinessHours(trimToNull(req.getBusinessHours()));
        store.setSeatCount(req.getSeatCount());
        storeRepository.save(store);

        StoreSetting setting = existingSetting;
        setting.setTaxRounding(taxRounding.toUpperCase(Locale.ROOT));
        setting.setPriceIncludesTax(req.isPriceIncludesTax());
        setting.setInvoiceRegNo(trimToNull(req.getInvoiceRegNo()));
        setting.setWebReservationMode(webReservationMode.toUpperCase(Locale.ROOT));
        setting.setCancelChargeDefaultCustomer(req.isCancelChargeDefaultCustomer());
        setting.setCancelChargeDefaultStore(req.isCancelChargeDefaultStore());
        setting.setRequireManagerApprovalForServeCancel(req.isRequireManagerApprovalForServeCancel());
        setting.setRequireManagerApprovalForVoidRefund(req.isRequireManagerApprovalForVoidRefund());
        storeSettingRepository.save(setting);

        auditLogService.recordForCurrentUser(AuditActions.STORE_SETTING_CHANGE, storeId, "STORE", storeId,
                beforeSummary, summarizeStoreSettings(store, setting));

        return toSettingsResponse(store, setting);
    }

    private static String summarizeStoreSettings(Store store, StoreSetting setting) {
        return "name=" + store.getName()
                + ", address=" + store.getAddress()
                + ", phone=" + store.getPhone()
                + ", businessHours=" + store.getBusinessHours()
                + ", seatCount=" + store.getSeatCount()
                + ", taxRounding=" + setting.getTaxRounding()
                + ", priceIncludesTax=" + setting.isPriceIncludesTax()
                + ", invoiceRegNo=" + setting.getInvoiceRegNo()
                + ", webReservationMode=" + setting.getWebReservationMode()
                + ", cancelChargeDefaultCustomer=" + setting.isCancelChargeDefaultCustomer()
                + ", cancelChargeDefaultStore=" + setting.isCancelChargeDefaultStore()
                + ", requireManagerApprovalForServeCancel=" + setting.isRequireManagerApprovalForServeCancel()
                + ", requireManagerApprovalForVoidRefund=" + setting.isRequireManagerApprovalForVoidRefund();
    }

    private StoreResponse toStoreResponse(Store store) {
        return new StoreResponse(
                store.getId(), store.getName(), store.getAddress(), store.getPhone(),
                store.getBusinessHours(), store.getSeatCount(), store.getTimezone(), store.isActive());
    }

    private StoreSettingsResponse toSettingsResponse(Store store, StoreSetting setting) {
        return new StoreSettingsResponse(
                store.getId(), store.getName(), store.getAddress(), store.getPhone(),
                store.getBusinessHours(), store.getSeatCount(),
                setting.getTaxRounding(), setting.isPriceIncludesTax(), setting.getInvoiceRegNo(),
                setting.getWebReservationMode(), setting.isCancelChargeDefaultCustomer(),
                setting.isCancelChargeDefaultStore(), setting.isRequireManagerApprovalForServeCancel(),
                setting.isRequireManagerApprovalForVoidRefund());
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
