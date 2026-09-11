package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** GET /api/v1/users・PUT /api/v1/users/{userId} のレスポンス1件。ユーザー管理画面の一覧・編集用。 */
@Data
@AllArgsConstructor
public class UserSummaryResponse {
    private Long id;
    private String name;
    private String email;
    /** OWNER / MANAGER / HALL / KITCHEN / PARTTIME */
    private String role;
    /** ACTIVE / LOCKED */
    private String status;
    /** null = 全店（未設定）。 */
    private Long storeId;
    private String storeName;
}
