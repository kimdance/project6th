package com.shopsystem.backend.dto;

import lombok.Data;

/**
 * PUT /api/v1/users/{userId} のリクエストボディ。ユーザー登録画面では選べない
 * 役割（ロール）と所属店舗を、経営管理者がここで変更する（02_requirements.md FR-A03）。
 * 退職（退会）処理もここで行う（status を RETIRED にする）。
 */
@Data
public class UserUpdateRequest {
    /** OWNER / MANAGER / HALL / KITCHEN / PARTTIME */
    private String role;
    /** null = 全店（未設定）に戻す。 */
    private Long storeId;
    /** ACTIVE（在籍中）／RETIRED（退職済み）。LOCKED はここでは指定できない（FR-A08専用）。 */
    private String status;
}
