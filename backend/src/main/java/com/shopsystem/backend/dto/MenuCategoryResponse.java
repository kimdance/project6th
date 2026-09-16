package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** メニューカテゴリのレスポンス（FR-D02）。 */
@Data
@AllArgsConstructor
public class MenuCategoryResponse {
    private Long id;
    private String name;
    private int displayOrder;
    private boolean active;
}
