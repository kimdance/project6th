package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.MenuCategoryRequest;
import com.shopsystem.backend.dto.MenuCategoryResponse;
import com.shopsystem.backend.dto.MenuItemPhotoResponse;
import com.shopsystem.backend.dto.MenuItemRequest;
import com.shopsystem.backend.dto.MenuItemResponse;
import com.shopsystem.backend.entity.MenuCategory;
import com.shopsystem.backend.entity.MenuItem;
import com.shopsystem.backend.entity.Store;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.repository.MenuCategoryRepository;
import com.shopsystem.backend.repository.MenuItemRepository;
import com.shopsystem.backend.web.TenantContext;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * メニューカテゴリ・メニュー項目の管理（FR-D01〜D03。04_architecture.md §6.3）。
 * フルの編集（登録・並べ替え・価格変更等）は経営管理者・店長のみ（{@link StoreAccessGuard#requireCanEdit}）、
 * 売り切れ・提供停止の切替はホール・キッチンも行える（{@link StoreAccessGuard#requireCanToggleMenuStatus}）。
 * 期間限定メニュー（FR-D04）とトッピング等の簡易オプション（FR-D05）はフェーズ1未実装。
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private static final Set<String> VALID_TAX_CATEGORIES = Set.of("STANDARD_10", "REDUCED_8");
    private static final Set<String> VALID_PREP_TYPES = Set.of("COOK", "NO_COOK");
    private static final Set<String> VALID_SALES_STATUSES = Set.of("ON_SALE", "SOLD_OUT", "SUSPENDED");

    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final MessageSource messageSource;
    private final StoreAccessGuard accessGuard;
    private final AuditLogService auditLogService;
    private final FileStorageService fileStorageService;

    // ---- カテゴリ（FR-D02） ----

    public List<MenuCategoryResponse> listCategories(Long storeId) {
        accessGuard.requireCanView(storeId);
        return menuCategoryRepository.findAllByStore_IdOrderByDisplayOrderAscIdAsc(storeId).stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    @Transactional
    public MenuCategoryResponse createCategory(Long storeId, MenuCategoryRequest req) {
        Store store = accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanEdit(storeId);

        String name = validateCategory(req);

        MenuCategory category = new MenuCategory();
        category.setCompanyCode(TenantContext.get().companyCode());
        category.setStore(store);
        category.setName(name);
        category.setDisplayOrder(req.getDisplayOrder());
        category.setActive(req.isActive());
        category = menuCategoryRepository.save(category);

        auditLogService.recordForCurrentUser(AuditActions.MENU_CHANGE, storeId, "MENU_CATEGORY", category.getId(),
                null, summarizeCategory(category));

        return toCategoryResponse(category);
    }

    /**
     * カテゴリの更新（FR-D02）。有効から無効へ切り替える場合、配下の全メニュー項目の販売状況を
     * 一括で「提供停止」にする（{@link #updateSalesStatus} が課す「所属カテゴリが無効なら
     * 提供停止のみ許可」を、無効化した瞬間から矛盾なく成立させるため）。メニュー項目自体の
     * 有効・無効（{@code is_active}）は連動させない（配下の商品を残したまま一時的に畳む運用も
     * 想定するため）。
     */
    @Transactional
    public MenuCategoryResponse updateCategory(Long storeId, Long categoryId, MenuCategoryRequest req) {
        accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanEdit(storeId);

        MenuCategory category = menuCategoryRepository.findByIdAndStore_Id(categoryId, storeId)
                .orElseThrow(accessGuard::notFound);
        String beforeSummary = summarizeCategory(category);
        boolean wasActive = category.isActive();

        String name = validateCategory(req);

        category.setName(name);
        category.setDisplayOrder(req.getDisplayOrder());
        category.setActive(req.isActive());
        menuCategoryRepository.save(category);

        auditLogService.recordForCurrentUser(AuditActions.MENU_CHANGE, storeId, "MENU_CATEGORY", category.getId(),
                beforeSummary, summarizeCategory(category));

        if (wasActive && !req.isActive()) {
            suspendAllItemsInCategory(storeId, category);
        }

        return toCategoryResponse(category);
    }

    private void suspendAllItemsInCategory(Long storeId, MenuCategory category) {
        for (MenuItem item : menuItemRepository.findAllByCategory_Id(category.getId())) {
            if ("SUSPENDED".equals(item.getSalesStatus())) {
                continue;
            }
            String beforeItemSummary = summarizeItem(item);
            item.setSalesStatus("SUSPENDED");
            menuItemRepository.save(item);
            auditLogService.recordForCurrentUser(AuditActions.MENU_CHANGE, storeId, "MENU_ITEM", item.getId(),
                    beforeItemSummary, summarizeItem(item));
        }
    }

    // ---- メニュー項目（FR-D01・D03） ----

    public List<MenuItemResponse> listItems(Long storeId) {
        accessGuard.requireCanView(storeId);
        return menuItemRepository.findAllByStore_IdOrderByDisplayOrderAscIdAsc(storeId).stream()
                .map(this::toItemResponse)
                .toList();
    }

    @Transactional
    public MenuItemResponse createItem(Long storeId, MenuItemRequest req) {
        Store store = accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanEdit(storeId);

        MenuCategory category = validateItem(storeId, req, null);

        MenuItem item = new MenuItem();
        item.setCompanyCode(TenantContext.get().companyCode());
        item.setStore(store);
        applyRequest(item, category, req);
        if (!req.isActive() || !category.isActive()) {
            // 新規登録時は販売状況を選べないためエンティティの既定値（ON_SALE）のままだと、
            // 「メニュー項目・所属カテゴリが無効なら提供停止のみ」という不変条件
            // （updateSalesStatus参照）に反した状態で保存されてしまう。無効な項目・無効な
            // カテゴリへの登録では、ここで提供停止にしておく。
            item.setSalesStatus("SUSPENDED");
        }
        item = menuItemRepository.save(item);

        auditLogService.recordForCurrentUser(AuditActions.MENU_CHANGE, storeId, "MENU_ITEM", item.getId(),
                null, summarizeItem(item));

        return toItemResponse(item);
    }

    @Transactional
    public MenuItemResponse updateItem(Long storeId, Long itemId, MenuItemRequest req) {
        accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanEdit(storeId);

        MenuItem item = menuItemRepository.findByIdAndStore_Id(itemId, storeId)
                .orElseThrow(accessGuard::notFound);
        String beforeSummary = summarizeItem(item);

        MenuCategory category = validateItem(storeId, req, item.getSalesStatus());

        applyRequest(item, category, req);
        if (!category.isActive() && !"SUSPENDED".equals(item.getSalesStatus())) {
            // カテゴリ選択欄には無効なカテゴリも表示されるため、既存の有効な商品を無効カテゴリへ
            // 付け替えることができてしまう。その場合も「無効カテゴリなら提供停止のみ」の不変
            // 条件を保つため、ここで提供停止に補正する（updateCategoryの一括提供停止と対）。
            item.setSalesStatus("SUSPENDED");
        }
        menuItemRepository.save(item);

        auditLogService.recordForCurrentUser(AuditActions.MENU_CHANGE, storeId, "MENU_ITEM", item.getId(),
                beforeSummary, summarizeItem(item));

        return toItemResponse(item);
    }

    /**
     * メニュー写真のアップロード（FR-D01）。フルの編集と同じ権限（経営管理者・店長のみ）で、
     * 保存自体は {@link FileStorageService} に委ねる。返す {@code photoUrl} を
     * メニュー項目の登録・更新リクエストの {@code photoUrl} にそのまま渡す想定。
     */
    public MenuItemPhotoResponse uploadItemPhoto(Long storeId, MultipartFile file) {
        accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanEdit(storeId);

        String photoUrl = fileStorageService.storeMenuItemPhoto(TenantContext.get().companyCode(), storeId, file);
        return new MenuItemPhotoResponse(photoUrl);
    }

    /**
     * 売り切れ・提供停止の切替（FR-D03）。メニュー項目自体が無効（{@code active == false}）な
     * 場合、または所属カテゴリ（{@code menu_category}）が無効な場合は、提供停止以外への変更を
     * 一切許さない（無効なメニュー・無効カテゴリ配下は事実上お客様に見えない前提のため）。
     * 前者は {@link #validateItem} が課す「無効化する前に提供停止にしておく」制約の裏返しで、
     * 先に {@link #updateItem} で有効化してから変更する。後者はカテゴリを再度有効化するか、
     * カテゴリ側で配下メニューを一括提供停止にする {@link #updateCategory} の処理と対になる。
     */
    @Transactional
    public MenuItemResponse updateSalesStatus(Long storeId, Long itemId, String salesStatus) {
        accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanToggleMenuStatus(storeId);

        MenuItem item = menuItemRepository.findByIdAndStore_Id(itemId, storeId)
                .orElseThrow(accessGuard::notFound);
        String beforeSummary = summarizeItem(item);

        if (salesStatus == null || !VALID_SALES_STATUSES.contains(salesStatus)) {
            throw new BusinessException(List.of(err("menu.error.sales-status.invalid", "salesStatus")));
        }
        if (!"SUSPENDED".equals(salesStatus)) {
            if (!item.isActive()) {
                throw new BusinessException(List.of(err("menu.error.sales-status.requires-active", "salesStatus")));
            }
            if (!item.getCategory().isActive()) {
                throw new BusinessException(List.of(err("menu.error.sales-status.category-inactive", "salesStatus")));
            }
        }
        item.setSalesStatus(salesStatus);
        menuItemRepository.save(item);

        auditLogService.recordForCurrentUser(AuditActions.MENU_CHANGE, storeId, "MENU_ITEM", item.getId(),
                beforeSummary, summarizeItem(item));

        return toItemResponse(item);
    }

    // ---- 内部処理 ----

    private void applyRequest(MenuItem item, MenuCategory category, MenuItemRequest req) {
        item.setCategory(category);
        item.setName(req.getName().trim());
        item.setDescription(trimToNull(req.getDescription()));
        item.setPriceJpy(req.getPriceJpy());
        item.setTaxCategory(req.getTaxCategory());
        item.setPrepType(req.getPrepType());
        item.setPhotoUrl(trimToNull(req.getPhotoUrl()));
        item.setServeTimeFrom(req.getServeTimeFrom());
        item.setServeTimeTo(req.getServeTimeTo());
        item.setDisplayOrder(req.getDisplayOrder());
        item.setActive(req.isActive());
    }

    private String validateCategory(MenuCategoryRequest req) {
        List<ErrorItem> errors = new ArrayList<>();
        String name = trimToNull(req.getName());
        if (name == null) {
            errors.add(err("menu.error.category-name.required", "name"));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }
        return name;
    }

    /**
     * フィールド検証とカテゴリの存在確認をまとめて行い、有効なカテゴリを返す。
     * {@code currentSalesStatus} は更新対象の既存メニュー項目の現在の販売状況（新規登録時は
     * {@code null}）。無効化する（{@code req.isActive() == false}）場合は、既にお客様へ提供
     * されなくなっている状態にしてから畳む運用とするため、事前に「提供停止」へ切り替えておく
     * ことを必須とする。新規登録時はこのチェックを行わない（登録直後は必ず販売中スタートで、
     * 提供停止へ切り替える手段が登録前には無いため）。
     */
    private MenuCategory validateItem(Long storeId, MenuItemRequest req, String currentSalesStatus) {
        List<ErrorItem> errors = new ArrayList<>();
        if (!req.isActive() && currentSalesStatus != null && !"SUSPENDED".equals(currentSalesStatus)) {
            errors.add(err("menu.error.sales-status.requires-active", "active"));
        }
        if (trimToNull(req.getName()) == null) {
            errors.add(err("menu.error.item-name.required", "name"));
        }
        if (req.getPriceJpy() < 0) {
            errors.add(err("menu.error.price.invalid", "priceJpy"));
        }
        if (req.getTaxCategory() == null || !VALID_TAX_CATEGORIES.contains(req.getTaxCategory())) {
            errors.add(err("menu.error.tax-category.invalid", "taxCategory"));
        }
        if (req.getPrepType() == null || !VALID_PREP_TYPES.contains(req.getPrepType())) {
            errors.add(err("menu.error.prep-type.invalid", "prepType"));
        }
        if ((req.getServeTimeFrom() == null) != (req.getServeTimeTo() == null)) {
            errors.add(err("menu.error.serve-time.incomplete", "serveTimeFrom"));
        }

        MenuCategory category = null;
        if (req.getCategoryId() == null) {
            errors.add(err("menu.error.category-id.required", "categoryId"));
        } else {
            category = menuCategoryRepository.findByIdAndStore_Id(req.getCategoryId(), storeId).orElse(null);
            if (category == null) {
                errors.add(err("menu.error.category-id.invalid", "categoryId"));
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }
        return category;
    }

    private String summarizeCategory(MenuCategory category) {
        return "name=" + category.getName() + ", displayOrder=" + category.getDisplayOrder()
                + ", active=" + category.isActive();
    }

    private String summarizeItem(MenuItem item) {
        return "name=" + item.getName() + ", categoryId=" + item.getCategory().getId()
                + ", priceJpy=" + item.getPriceJpy() + ", salesStatus=" + item.getSalesStatus()
                + ", active=" + item.isActive();
    }

    private MenuCategoryResponse toCategoryResponse(MenuCategory category) {
        return new MenuCategoryResponse(
                category.getId(), category.getName(), category.getDisplayOrder(), category.isActive());
    }

    private MenuItemResponse toItemResponse(MenuItem item) {
        return new MenuItemResponse(
                item.getId(), item.getCategory().getId(), item.getCategory().getName(),
                item.getName(), item.getDescription(), item.getPriceJpy(), item.getTaxCategory(),
                item.getPrepType(), item.getPhotoUrl(), item.getServeTimeFrom(), item.getServeTimeTo(),
                item.getSalesStatus(), item.getDisplayOrder(), item.isActive());
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
