package com.shopsystem.backend.web;

import com.shopsystem.backend.exception.UnauthorizedException;
import com.shopsystem.backend.security.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ログイン済みが前提の業務APIの関所（04_architecture.md §3.2）。
 * {@code Authorization: Bearer <アクセストークン>} を検証し、クレームから
 * {@link TenantContext} を組み立てる。あわせて「JWTのcompanyCode ＝ サブドメイン」の一致を検証し、
 * 別テナントのサブドメインでトークンを使い回すことを防ぐ。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final MessageSource messageSource;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            // CORSのプリフライト（OPTIONS）は認証の対象外。CorsConfig側でヘッダのみ付与する。
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw unauthorized();
        }
        String token = header.substring(BEARER_PREFIX.length());

        Claims claims;
        try {
            claims = jwtService.parseAccessToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw unauthorized();
        }

        String companyCodeFromToken = claims.get("companyCode", String.class);
        String companyCodeFromHost = TenantResolutionInterceptor.extractCompanyCode(request.getHeader("Host"));
        if (companyCodeFromToken == null || !companyCodeFromToken.equals(companyCodeFromHost)) {
            // 他テナントのサブドメインでトークンを使い回すことを防ぐ（04_architecture.md §3.2）。
            throw unauthorized();
        }

        Long companyId = claims.get("companyId", Long.class);
        Long userId = Long.valueOf(claims.getSubject());
        String role = claims.get("role", String.class);
        List<?> rawStoreIds = claims.get("storeIds", List.class);
        Set<Long> storeIds = rawStoreIds == null
                ? Set.of()
                : rawStoreIds.stream().map(id -> ((Number) id).longValue()).collect(Collectors.toSet());

        TenantContext.set(new TenantContext.Data(companyId, companyCodeFromToken, userId, role, storeIds));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        TenantContext.clear();
    }

    private UnauthorizedException unauthorized() {
        return new UnauthorizedException(
                messageSource.getMessage("auth.error.unauthorized", null, LocaleContextHolder.getLocale()));
    }
}
