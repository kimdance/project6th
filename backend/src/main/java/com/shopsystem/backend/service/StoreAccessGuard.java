package com.shopsystem.backend.service;

import com.shopsystem.backend.entity.Store;
import com.shopsystem.backend.exception.ForbiddenException;
import com.shopsystem.backend.exception.NotFoundException;
import com.shopsystem.backend.repository.StoreRepository;
import com.shopsystem.backend.web.TenantContext;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * 店舗配下リソース（設定・卓・決済手段・営業日）に共通するテナント判定・権限チェック
 * （`02_requirements.md` §3.2 の権限マトリクス／04_architecture.md §3.2）。
 */
@Component
@RequiredArgsConstructor
public class StoreAccessGuard {

    private final StoreRepository storeRepository;
    private final MessageSource messageSource;

    /** storeId が呼び出し元のテナント外なら404（存在有無は漏らさない）。 */
    public Store requireStoreInTenant(Long storeId) {
        TenantContext.Data ctx = TenantContext.get();
        return storeRepository.findByIdAndCompany_Id(storeId, ctx.companyId()).orElseThrow(this::notFound);
    }

    /**
     * 店舗設定・卓・決済手段・営業日の閲覧範囲：オーナーは全店、店長は自分が所属する店舗
     * （複数可）のみ、それ以外のロール（ホール／キッチン／バイト）は引き続き閲覧可
     * （編集権限が無いだけで、閲覧はテナント内であれば制限しない従来の方針を維持）。
     */
    public Store requireCanView(Long storeId) {
        Store store = requireStoreInTenant(storeId);
        TenantContext.Data ctx = TenantContext.get();
        if ("MANAGER".equals(ctx.role()) && !ctx.storeIds().contains(storeId)) {
            throw forbidden();
        }
        return store;
    }

    /** オーナーは全店、店長は自分が所属する店舗（複数可）のみ編集可。それ以外のロールは編集不可。 */
    public void requireCanEdit(Long storeId) {
        TenantContext.Data ctx = TenantContext.get();
        boolean allowed = "OWNER".equals(ctx.role())
                || ("MANAGER".equals(ctx.role()) && ctx.storeIds().contains(storeId));
        if (!allowed) {
            throw forbidden();
        }
    }

    /**
     * 予約の登録・変更・キャンセル（FR-C01）：経営管理者は全店、店長・ホールは自分が
     * 所属する店舗（複数可）のみ（`02_requirements.md` §3.2）。キッチン・バイトは不可。
     */
    public void requireCanManageReservations(Long storeId) {
        TenantContext.Data ctx = TenantContext.get();
        boolean allowed = "OWNER".equals(ctx.role())
                || (("MANAGER".equals(ctx.role()) || "HALL".equals(ctx.role())) && ctx.storeIds().contains(storeId));
        if (!allowed) {
            throw forbidden();
        }
    }

    public void requireOwner() {
        if (!"OWNER".equals(TenantContext.get().role())) {
            throw forbidden();
        }
    }

    public NotFoundException notFound() {
        return new NotFoundException(
                messageSource.getMessage("store.error.not-found", null, LocaleContextHolder.getLocale()));
    }

    public ForbiddenException forbidden() {
        return new ForbiddenException(
                messageSource.getMessage("store.error.forbidden", null, LocaleContextHolder.getLocale()));
    }
}
