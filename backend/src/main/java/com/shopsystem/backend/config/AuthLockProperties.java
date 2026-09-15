package com.shopsystem.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ログイン失敗の連続回数による一時ロック（app.auth.*・FR-A08）。
 * maxFailedAttempts 回連続で失敗すると lockDurationMinutes 分だけロックし、
 * 経過後の次回アクセスでアプリ層が自動解除する。
 */
@Component
@ConfigurationProperties(prefix = "app.auth")
@Data
public class AuthLockProperties {

    /** この回数だけ連続でログインに失敗するとロックする。 */
    private int maxFailedAttempts = 5;

    /** ロックの継続時間（分）。 */
    private long lockDurationMinutes = 15;
}
