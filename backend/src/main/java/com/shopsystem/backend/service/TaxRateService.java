package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.TaxRateRequest;
import com.shopsystem.backend.dto.TaxRateResponse;
import com.shopsystem.backend.entity.Store;
import com.shopsystem.backend.entity.TaxRate;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.exception.ConflictException;
import com.shopsystem.backend.repository.TaxRateRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 税率（標準・軽減）の管理（FR-B03。04_architecture.md 追補を参照）。区分のコード
 * （STANDARD_10／REDUCED_8）自体は変更せず、実際のパーセンテージだけを店舗ごとに変更できる。
 * 終了日は持たず、同じ区分の中で {@code effectiveFrom} が最も新しい（今日以前の）行が
 * そのとき有効な税率になる（{@link #resolveRatePercent}。会計の税額計算〈CheckoutService〉で使う）。
 */
@Service
@RequiredArgsConstructor
public class TaxRateService {

    private static final Set<String> VALID_TAX_CATEGORIES = Set.of("STANDARD_10", "REDUCED_8");
    private static final BigDecimal MAX_RATE_PERCENT = BigDecimal.valueOf(100);

    private final TaxRateRepository taxRateRepository;
    private final MessageSource messageSource;
    private final StoreAccessGuard accessGuard;
    private final AuditLogService auditLogService;

    public List<TaxRateResponse> list(Long storeId) {
        accessGuard.requireCanView(storeId);
        LocalDate today = LocalDate.now();
        // findAllBy...OrderByTaxCategoryAscEffectiveFromDesc なので、同じ区分の中では
        // 先頭（今日以前で最初に出てくる行）が現在有効な税率。
        Set<String> seenCategory = new HashSet<>();
        List<TaxRate> rates = taxRateRepository.findAllByStore_IdOrderByTaxCategoryAscEffectiveFromDesc(storeId);

        List<TaxRateResponse> result = new ArrayList<>();
        for (TaxRate r : rates) {
            boolean isCurrent = !r.getEffectiveFrom().isAfter(today) && seenCategory.add(r.getTaxCategory());
            result.add(new TaxRateResponse(
                    r.getId(), r.getTaxCategory(), r.getRatePercent(), r.getEffectiveFrom(), isCurrent));
        }
        return result;
    }

    @Transactional
    public TaxRateResponse create(Long storeId, TaxRateRequest req) {
        Store store = accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanEdit(storeId);

        List<ErrorItem> errors = new ArrayList<>();
        String category = req.getTaxCategory();
        if (category == null || !VALID_TAX_CATEGORIES.contains(category)) {
            errors.add(err("tax-rate.error.category.invalid", "taxCategory"));
        }
        if (req.getRatePercent() == null || req.getRatePercent().signum() < 0
                || req.getRatePercent().compareTo(MAX_RATE_PERCENT) > 0) {
            errors.add(err("tax-rate.error.rate.invalid", "ratePercent"));
        }
        if (req.getEffectiveFrom() == null) {
            errors.add(err("tax-rate.error.effective-from.required", "effectiveFrom"));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }
        if (taxRateRepository.existsByStore_IdAndTaxCategoryAndEffectiveFrom(
                storeId, category, req.getEffectiveFrom())) {
            throw new ConflictException(
                    messageSource.getMessage("tax-rate.error.effective-from.duplicate", null,
                            LocaleContextHolder.getLocale()));
        }

        TaxRate rate = new TaxRate();
        rate.setStore(store);
        rate.setTaxCategory(category);
        rate.setRatePercent(req.getRatePercent());
        rate.setEffectiveFrom(req.getEffectiveFrom());
        rate = taxRateRepository.save(rate);

        auditLogService.recordForCurrentUser(AuditActions.STORE_SETTING_CHANGE, storeId, "TAX_RATE", rate.getId(),
                null, summarize(rate));

        return toResponse(rate, storeId);
    }

    @Transactional
    public void delete(Long storeId, Long rateId) {
        accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanEdit(storeId);

        TaxRate rate = taxRateRepository.findByIdAndStore_Id(rateId, storeId).orElseThrow(accessGuard::notFound);
        if (!rate.getEffectiveFrom().isAfter(LocalDate.now())) {
            throw new BusinessException(List.of(err("tax-rate.error.delete.already-effective", null)));
        }
        taxRateRepository.delete(rate);

        auditLogService.recordForCurrentUser(AuditActions.STORE_SETTING_CHANGE, storeId, "TAX_RATE", rateId,
                summarize(rate), null);
    }

    /**
     * 会計時点（businessDate）で有効な税率をパーセントで返す。店舗が一度もこの区分の税率を
     * 設定していなければ、フェーズ1導入時点の既定値（STANDARD_10=10%／REDUCED_8=8%）を返す。
     */
    public BigDecimal resolveRatePercent(Long storeId, String taxCategory, LocalDate businessDate) {
        return taxRateRepository
                .findFirstByStore_IdAndTaxCategoryAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        storeId, taxCategory, businessDate)
                .map(TaxRate::getRatePercent)
                .orElse("STANDARD_10".equals(taxCategory) ? BigDecimal.valueOf(10) : BigDecimal.valueOf(8));
    }

    private TaxRateResponse toResponse(TaxRate rate, Long storeId) {
        LocalDate today = LocalDate.now();
        Long currentId = taxRateRepository
                .findFirstByStore_IdAndTaxCategoryAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        storeId, rate.getTaxCategory(), today)
                .map(TaxRate::getId)
                .orElse(null);
        boolean isCurrent = rate.getId().equals(currentId);
        return new TaxRateResponse(
                rate.getId(), rate.getTaxCategory(), rate.getRatePercent(), rate.getEffectiveFrom(), isCurrent);
    }

    private String summarize(TaxRate rate) {
        return "taxCategory=" + rate.getTaxCategory() + ", ratePercent=" + rate.getRatePercent()
                + ", effectiveFrom=" + rate.getEffectiveFrom();
    }

    private ErrorItem err(String code, String field) {
        Locale locale = LocaleContextHolder.getLocale();
        return new ErrorItem(
                messageSource.getMessage(code, null, locale), field == null ? List.of() : List.of(field));
    }
}
