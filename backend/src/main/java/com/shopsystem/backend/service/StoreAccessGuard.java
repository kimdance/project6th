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

import java.util.Set;

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

    /**
     * メニューの売り切れ・提供停止の切替（FR-D03）：経営管理者は全店、店長・ホール・キッチンは
     * 自分が所属する店舗（複数可）のみ。バイトは不可（`02_requirements.md` §3.2）。
     * フルの編集（登録・価格変更等）は {@link #requireCanEdit} のとおり経営管理者・店長のみ。
     */
    public void requireCanToggleMenuStatus(Long storeId) {
        TenantContext.Data ctx = TenantContext.get();
        boolean allowed = "OWNER".equals(ctx.role())
                || (Set.of("MANAGER", "HALL", "KITCHEN").contains(ctx.role()) && ctx.storeIds().contains(storeId));
        if (!allowed) {
            throw forbidden();
        }
    }

    /**
     * 卓のオープン／クローズ、注文の入力・数量変更・取消、会計の作成・確定・取消・返金・値引き
     * （FR-E01〜E03、FR-G01〜）：経営管理者は全店、店長・ホール・キッチンは自分が所属する店舗
     * （複数可）のみ（`02_requirements.md` §3.2。2026-09-19改訂：小規模店舗では同じスタッフが
     * ホールとキッチンを兼ねる運用があるため、キッチンをホールと同じ権限セットにした）。
     * バイトは不可。
     */
    public void requireCanManageFloor(Long storeId) {
        TenantContext.Data ctx = TenantContext.get();
        boolean allowed = "OWNER".equals(ctx.role())
                || (Set.of("MANAGER", "HALL", "KITCHEN").contains(ctx.role()) && ctx.storeIds().contains(storeId));
        if (!allowed) {
            throw forbidden();
        }
    }

    /**
     * 提供後の注文明細の取消（FR-E03）：店舗設定で「要店長承認」が有効な場合は経営管理者・
     * 店長のみ、無効な場合は {@link #requireCanManageFloor} と同じ（ホール・キッチンも可）。
     */
    public void requireCanCancelServedLine(Long storeId, boolean requireManagerApproval) {
        if (!requireManagerApproval) {
            requireCanManageFloor(storeId);
            return;
        }
        TenantContext.Data ctx = TenantContext.get();
        boolean allowed = "OWNER".equals(ctx.role())
                || ("MANAGER".equals(ctx.role()) && ctx.storeIds().contains(storeId));
        if (!allowed) {
            throw forbidden();
        }
    }

    /**
     * 会計の取消・返金・値引き（FR-G10）：店舗設定で「要店長承認」が有効な場合は経営管理者・
     * 店長のみ、無効な場合は {@link #requireCanManageFloor} と同じ（ホール・キッチンも可）。
     */
    public void requireCanAdjustCheck(Long storeId, boolean requireManagerApproval) {
        if (!requireManagerApproval) {
            requireCanManageFloor(storeId);
            return;
        }
        TenantContext.Data ctx = TenantContext.get();
        boolean allowed = "OWNER".equals(ctx.role())
                || ("MANAGER".equals(ctx.role()) && ctx.storeIds().contains(storeId));
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
