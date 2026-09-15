package com.shopsystem.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 無操作セッションタイムアウト（app.session.*・FR-A09）。
 * ログイン・リフレッシュ成功のたびに最終操作時刻を更新し、idleTimeoutMinutes を超えて
 * 更新がなければ次のリフレッシュを拒否し、再ログインを求める。
 */
@Component
@ConfigurationProperties(prefix = "app.session")
@Data
public class SessionProperties {

    /** この時間（分）操作（ログイン・リフレッシュ）がなければセッションを無効とする。 */
    private long idleTimeoutMinutes = 30;
}
