package com.shopsystem.backend.security;

import com.shopsystem.backend.config.OperatorProperties;
import com.shopsystem.backend.exception.ForbiddenException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 運営者専用エンドポイントの合言葉チェック。
 * app.operator.provision-token が未設定なら常に拒否（機能オフ）、設定済みなら一致時のみ通す。
 * フェーズ2で運営者認証（セッション/JWT）へ置き換える。
 */
@Component
@RequiredArgsConstructor
public class OperatorTokenGuard {

    public static final String HEADER = "X-Operator-Token";

    private final OperatorProperties properties;

    /** 合言葉が未設定、または送られた値と一致しなければ {@link ForbiddenException}。 */
    public void verify(String presentedToken) {
        String expected = properties.getProvisionToken();
        if (expected == null || expected.isBlank()) {
            throw new ForbiddenException("運営者向け機能は無効です。");
        }
        if (presentedToken == null || !constantTimeEquals(expected, presentedToken)) {
            throw new ForbiddenException("操作が許可されていません。");
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
