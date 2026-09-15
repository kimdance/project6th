package com.shopsystem.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 決済手段の接続情報（FR-B05）を暗号化して保存するための鍵（app.crypto.*）。
 * 本番環境では必ず環境変数で上書きする。
 */
@Component
@ConfigurationProperties(prefix = "app.crypto")
@Data
public class CryptoProperties {

    /** 暗号化のパスフレーズ。 */
    private String secret;

    /** ソルト（16進数文字列）。 */
    private String salt;
}
