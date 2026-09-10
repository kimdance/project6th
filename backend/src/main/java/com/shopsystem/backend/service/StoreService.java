package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.StoreCreateRequest;
import com.shopsystem.backend.dto.StoreResponse;
import com.shopsystem.backend.dto.StoreSettingsRequest;
import com.shopsystem.backend.dto.StoreSettingsResponse;
import com.shopsystem.backend.entity.Store;
import com.shopsystem.backend.entity.StoreSetting;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.exception.ForbiddenException;
import com.shopsystem.backend.exception.NotFoundException;
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
 * 権限は `02_requirements.md` §3.2 の権限マトリクスに従う：作成はオーナーのみ、設定編集は
 * オーナー（全店）／店長（自店のみ）、それ以外のロールは編集不可（閲覧は認証済みであれば可）。
 */
@Service
@RequiredArgsConstructor
public class StoreService {

    private static final Pattern TAX_ROUNDING = Pattern.compile("^(FLOOR|CEIL|ROUND)$");

    private final StoreRepository storeRepository;
    private final StoreSettingRepository storeSettingRepository;
    private final CompanyRepository companyRepository;
    private final MessageSource messageSource;

    @Transactional
    public StoreResponse create(StoreCreateRequest req) {
        TenantContext.Data ctx = TenantContext.get();
        requireOwner(ctx);

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

    public StoreSettingsResponse getSettings(Long storeId) {
        TenantContext.Data ctx = TenantContext.get();
        Store store = loadStoreInTenant(ctx, storeId);
        StoreSetting setting = storeSettingRepository.findById(storeId).orElseThrow(this::notFound);
        return toSettingsResponse(store, setting);
    }

    @Transactional
    public StoreSettingsResponse updateSettings(Long storeId, StoreSettingsRequest req) {
        TenantContext.Data ctx = TenantContext.get();
        Store store = loadStoreInTenant(ctx, storeId);
        requireCanEdit(ctx, storeId);

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
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }

        store.setName(name);
        store.setAddress(trimToNull(req.getAddress()));
        store.setPhone(trimToNull(req.getPhone()));
        store.setBusinessHours(trimToNull(req.getBusinessHours()));
        store.setSeatCount(req.getSeatCount());
        storeRepository.save(store);

        StoreSetting setting = storeSettingRepository.findById(storeId).orElseThrow(this::notFound);
        setting.setTaxRounding(taxRounding.toUpperCase(Locale.ROOT));
        setting.setPriceIncludesTax(req.isPriceIncludesTax());
        setting.setInvoiceRegNo(trimToNull(req.getInvoiceRegNo()));
        storeSettingRepository.save(setting);

        return toSettingsResponse(store, setting);
    }

    private Store loadStoreInTenant(TenantContext.Data ctx, Long storeId) {
        return storeRepository.findByIdAndCompany_Id(storeId, ctx.companyId()).orElseThrow(this::notFound);
    }

    private void requireOwner(TenantContext.Data ctx) {
        if (!"OWNER".equals(ctx.role())) {
            throw forbidden();
        }
    }

    /** オーナーは全店、店長は自店のみ編集可。それ以外のロールは編集不可（`02` §3.2）。 */
    private void requireCanEdit(TenantContext.Data ctx, Long storeId) {
        boolean allowed = "OWNER".equals(ctx.role())
                || ("MANAGER".equals(ctx.role()) && storeId.equals(ctx.storeId()));
        if (!allowed) {
            throw forbidden();
        }
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
                setting.getTaxRounding(), setting.isPriceIncludesTax(), setting.getInvoiceRegNo());
    }

    private ErrorItem err(String code, String field) {
        Locale locale = LocaleContextHolder.getLocale();
        return new ErrorItem(messageSource.getMessage(code, null, locale), List.of(field));
    }

    private NotFoundException notFound() {
        return new NotFoundException(
                messageSource.getMessage("store.error.not-found", null, LocaleContextHolder.getLocale()));
    }

    private ForbiddenException forbidden() {
        return new ForbiddenException(
                messageSource.getMessage("store.error.forbidden", null, LocaleContextHolder.getLocale()));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
