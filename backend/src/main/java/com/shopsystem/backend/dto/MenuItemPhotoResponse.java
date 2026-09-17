package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** メニュー写真アップロードのレスポンス（FR-D01）。 */
@Data
@AllArgsConstructor
public class MenuItemPhotoResponse {
    private String photoUrl;
}
