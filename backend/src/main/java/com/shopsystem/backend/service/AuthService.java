package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.LoginRequest;
import com.shopsystem.backend.dto.LoginResponse;
import com.shopsystem.backend.dto.TenantInfoResponse;
import com.shopsystem.backend.entity.User;
import com.shopsystem.backend.exception.UnauthorizedException;
import com.shopsystem.backend.repository.UserRepository;
import com.shopsystem.backend.security.JwtService;
import com.shopsystem.backend.web.TenantResolutionInterceptor;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * サブドメインから解決したテナント情報の参照とログイン・トークン更新（04_architecture.md §6.1）。
 * ログイン失敗の連続回数によるロック（FR-A08）とセッションの無操作タイムアウト（FR-A09）は対象外
 * （後回しとして合意済み）。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MessageSource messageSource;

    public TenantInfoResponse currentTenant(HttpServletRequest request) {
        String companyCode = (String) request.getAttribute(TenantResolutionInterceptor.ATTR_COMPANY_CODE);
        String companyName = (String) request.getAttribute(TenantResolutionInterceptor.ATTR_COMPANY_NAME);
        return new TenantInfoResponse(companyCode, companyName);
    }

    public LoginResponse login(HttpServletRequest request, LoginRequest body) {
        Long companyId = (Long) request.getAttribute(TenantResolutionInterceptor.ATTR_COMPANY_ID);
        String email = normalizeLower(body.getEmail());

        Optional<User> userOpt = email == null
                ? Optional.empty()
                : userRepository.findByCompany_IdAndEmail(companyId, email);

        boolean credentialsOk = userOpt.isPresent()
                && "ACTIVE".equals(userOpt.get().getStatus())
                && body.getPassword() != null
                && passwordEncoder.matches(body.getPassword(), userOpt.get().getPassword());

        if (!credentialsOk) {
            throw invalidCredentials();
        }

        User user = userOpt.get();
        return new LoginResponse(
                jwtService.issueAccessToken(user),
                jwtService.issueRefreshToken(user),
                "Bearer");
    }

    public LoginResponse refresh(HttpServletRequest request, String refreshToken) {
        String companyCode = (String) request.getAttribute(TenantResolutionInterceptor.ATTR_COMPANY_CODE);

        Claims claims;
        try {
            claims = jwtService.parseRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw invalidToken();
        }

        if (!companyCode.equals(claims.get("companyCode", String.class))) {
            // 他テナントのリフレッシュトークンを現在のサブドメインで使わせない（04_architecture.md §3.2）。
            throw invalidToken();
        }

        Long userId = Long.valueOf(claims.getSubject());
        User user = userRepository.findById(userId)
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .orElseThrow(this::invalidToken);

        return new LoginResponse(jwtService.issueAccessToken(user), refreshToken, "Bearer");
    }

    private UnauthorizedException invalidCredentials() {
        Locale locale = LocaleContextHolder.getLocale();
        return new UnauthorizedException(
                messageSource.getMessage("auth.error.invalid-credentials", null, locale));
    }

    private UnauthorizedException invalidToken() {
        Locale locale = LocaleContextHolder.getLocale();
        return new UnauthorizedException(
                messageSource.getMessage("auth.error.invalid-token", null, locale));
    }

    private static String normalizeLower(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t.toLowerCase(Locale.ROOT);
    }
}
