package com.shopsystem.backend.dto;

import lombok.Data;

/** POST/PUT /api/v1/stores/{storeId}/menu-categories[/{categoryId}] のリクエストボディ（FR-D02）。 */
@Data
public class MenuCategoryRequest {
    private String name;
    private int displayOrder;
    private boolean active = true;
}
