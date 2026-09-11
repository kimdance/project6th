package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * GET /api/v1/auth/me のレスポンス。ログイン後の共通トップ画面（04_architecture.md §6）で
 * 「ようこそ ◯◯さん」の表示と、ロールに応じたメニューの出し分けに使う。
 */
@Data
@AllArgsConstructor
public class MeResponse {
    private Long userId;
    private String name;
    private String email;
    private String telnumber;
    /** OWNER / MANAGER / HALL / KITCHEN / PARTTIME（02_requirements.md §3.1）。 */
    private String role;
    private String companyName;
    /** null = 全店（本部ユーザー等。04_architecture.md §4.3）。 */
    private Long storeId;
    private String storeName;
}
