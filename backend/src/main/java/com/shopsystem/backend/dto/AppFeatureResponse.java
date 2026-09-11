package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * GET /api/v1/app-features のレスポンス1件。ログイン後の共通トップ画面のカード表示用。
 * 呼び出し元のロールで表示可否をサーバ側でフィルタ済みのため、ロール情報は含めない。
 */
@Data
@AllArgsConstructor
public class AppFeatureResponse {
    private String key;
    private String title;
    private String description;
    private String path;
}
