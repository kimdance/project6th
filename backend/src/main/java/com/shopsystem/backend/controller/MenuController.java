package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.MenuCategoryRequest;
import com.shopsystem.backend.dto.MenuCategoryResponse;
import com.shopsystem.backend.dto.MenuItemRequest;
import com.shopsystem.backend.dto.MenuItemResponse;
import com.shopsystem.backend.dto.MenuItemStatusRequest;
import com.shopsystem.backend.service.MenuService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** メニューカテゴリ・メニュー項目の管理（FR-D01〜D03。04_architecture.md §6.3）。 */
@RestController
@RequestMapping("/api/v1/stores/{storeId}")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/menu-categories")
    public List<MenuCategoryResponse> listCategories(@PathVariable Long storeId) {
        return menuService.listCategories(storeId);
    }

    @PostMapping("/menu-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuCategoryResponse createCategory(@PathVariable Long storeId, @RequestBody MenuCategoryRequest body) {
        return menuService.createCategory(storeId, body);
    }

    @PutMapping("/menu-categories/{categoryId}")
    public MenuCategoryResponse updateCategory(
            @PathVariable Long storeId, @PathVariable Long categoryId, @RequestBody MenuCategoryRequest body) {
        return menuService.updateCategory(storeId, categoryId, body);
    }

    @GetMapping("/menu-items")
    public List<MenuItemResponse> listItems(@PathVariable Long storeId) {
        return menuService.listItems(storeId);
    }

    @PostMapping("/menu-items")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemResponse createItem(@PathVariable Long storeId, @RequestBody MenuItemRequest body) {
        return menuService.createItem(storeId, body);
    }

    @PutMapping("/menu-items/{itemId}")
    public MenuItemResponse updateItem(
            @PathVariable Long storeId, @PathVariable Long itemId, @RequestBody MenuItemRequest body) {
        return menuService.updateItem(storeId, itemId, body);
    }

    @PatchMapping("/menu-items/{itemId}/sales-status")
    public MenuItemResponse updateSalesStatus(
            @PathVariable Long storeId, @PathVariable Long itemId, @RequestBody MenuItemStatusRequest body) {
        return menuService.updateSalesStatus(storeId, itemId, body.getSalesStatus());
    }
}
