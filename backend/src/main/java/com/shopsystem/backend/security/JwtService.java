package com.shopsystem.backend.security;

import com.shopsystem.backend.config.JwtProperties;
import com.shopsystem.backend.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * ログイン（POST /api/v1/auth/login）・更新（POST /api/v1/auth/refresh）で使うJWTの発行・検証。
 * クレームは company_id／company_code／role／store_id（nullable）を含む（04_architecture.md §6.1）。
 * アクセストークンとリフレッシュトークンは "type" クレームで区別し、用途違いでの流用を防ぐ。
 */
@Component
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_COMPANY_ID = "companyId";
    private static final String CLAIM_COMPANY_CODE = "companyCode";
    private static final String CLAIM_STORE_ID = "storeId";
    private static final String CLAIM_ROLE = "role";

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(properties.getAccessTokenMinutes());
        this.refreshTokenTtl = Duration.ofDays(properties.getRefreshTokenDays());
    }

    public String issueAccessToken(User user) {
        return issue(user, TYPE_ACCESS, accessTokenTtl);
    }

    public String issueRefreshToken(User user) {
        return issue(user, TYPE_REFRESH, refreshTokenTtl);
    }

    /** アクセストークンとして検証する。type 不一致・署名不正・期限切れは {@link JwtException}。 */
    public Claims parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS);
    }

    /** リフレッシュトークンとして検証する。type 不一致・署名不正・期限切れは {@link JwtException}。 */
    public Claims parseRefreshToken(String token) {
        return parse(token, TYPE_REFRESH);
    }

    private String issue(User user, String type, Duration ttl) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_COMPANY_ID, user.getCompany().getId())
                .claim(CLAIM_COMPANY_CODE, user.getCompany().getCompanyCode())
                .claim(CLAIM_ROLE, user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key);
        if (user.getStore() != null) {
            builder.claim(CLAIM_STORE_ID, user.getStore().getId());
        }
        return builder.compact();
    }

    private Claims parse(String token, String expectedType) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload();
        if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("unexpected token type");
        }
        return claims;
    }
}
