package com.shopsystem.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * パスワード再設定メール（app.password-reset.*・FR-A04・EXT-03）。
 * リンクの有効期限と、メール本文に載せるフロントエンドのURLテンプレート
 * （{@code %s} に company_code が入る）を設定する。
 */
@Component
@ConfigurationProperties(prefix = "app.password-reset")
@Data
public class PasswordResetProperties {

    /** リンクの有効期限（分）。 */
    private int expiryMinutes = 30;

    /** {@code %s} に company_code を埋め込んでフロントのURLを組み立てる。 */
    private String frontendBaseUrlTemplate = "http://%s.localhost:5173";

    /** 送信元メールアドレス。 */
    private String mailFrom = "no-reply@shop-system.local";
}
