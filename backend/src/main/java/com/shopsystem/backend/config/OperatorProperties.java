package com.shopsystem.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 運営者向け設定（app.operator.*）。
 *
 * provisionToken は「テナント作成」受付（POST /api/v1/admin/tenants）の合言葉。
 * 未設定（空）のあいだは受付を常に拒否する＝既定では機能オフ。フェーズ2で運営者認証に置き換える。
 */
@Component
@ConfigurationProperties(prefix = "app.operator")
@Data
public class OperatorProperties {

    /** テナント作成受付の合言葉。空なら受付を無効化する。 */
    private String provisionToken;
}
