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

    @Transactional
    public MenuCategoryResponse updateCategory(Long storeId, Long categoryId, MenuCategoryRequest req) {
        accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanEdit(storeId);

        MenuCategory category = menuCategoryRepository.findByIdAndStore_Id(categoryId, storeId)
                .orElseThrow(accessGuard::notFound);
        String beforeSummary = summarizeCategory(category);

        String name = validateCategory(req);

        category.setName(name);
        category.setDisplayOrder(req.getDisplayOrder());
        category.setActive(req.isActive());
        menuCategoryRepository.save(category);

        auditLogService.recordForCurrentUser(AuditActions.MENU_CHANGE, storeId, "MENU_CATEGORY", category.getId(),
                beforeSummary, summarizeCategory(category));

        return toCategoryResponse(category);
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

        MenuCategory category = validateItem(storeId, req);

        MenuItem item = new MenuItem();
        item.setCompanyCode(TenantContext.get().companyCode());
        item.setStore(store);
        applyRequest(item, category, req);
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

        MenuCategory category = validateItem(storeId, req);

        applyRequest(item, category, req);
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

    /** フィールド検証とカテゴリの存在確認をまとめて行い、有効なカテゴリを返す。 */
    private MenuCategory validateItem(Long storeId, MenuItemRequest req) {
        List<ErrorItem> errors = new ArrayList<>();
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
