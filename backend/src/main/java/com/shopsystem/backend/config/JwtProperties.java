package com.shopsystem.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ログイン（POST /api/v1/auth/login）で発行するJWTの設定（app.jwt.*）。
 * secret は本番環境では必ず環境変数（APP_JWT_SECRET）で上書きする。04_architecture.md §6.1。
 */
@Component
@ConfigurationProperties(prefix = "app.jwt")
@Data
public class JwtProperties {

    /** HMAC署名鍵。HS256のため最低256bit（32バイト）必要。 */
    private String secret;

    /** アクセストークンの有効期限（分）。既定15分（§6.1）。 */
    private long accessTokenMinutes = 15;

    /** リフレッシュトークンの有効期限（日）。既定14日（§6.1）。 */
    private long refreshTokenDays = 14;
}
