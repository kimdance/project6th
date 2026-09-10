package com.shopsystem.backend.service;

import com.shopsystem.backend.config.AuthLockProperties;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * サブドメインから解決したテナント情報の参照とログイン・トークン更新（04_architecture.md §6.1）。
 * ログイン失敗が連続 {@link AuthLockProperties#getMaxFailedAttempts()} 回に達すると
 * {@link AuthLockProperties#getLockDurationMinutes()} 分の一時ロックを行う（FR-A08）。
 * ロック解除はバッチ処理を持たず、ロック中のユーザーへの次回アクセス時にアプリ層で判定する。
 * セッションの無操作タイムアウト（FR-A09）は対象外（後回しとして合意済み）。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_LOCKED = "LOCKED";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MessageSource messageSource;
    private final AuthLockProperties authLockProperties;

    public TenantInfoResponse currentTenant(HttpServletRequest request) {
        String companyCode = (String) request.getAttribute(TenantResolutionInterceptor.ATTR_COMPANY_CODE);
        String companyName = (String) request.getAttribute(TenantResolutionInterceptor.ATTR_COMPANY_NAME);
        return new TenantInfoResponse(companyCode, companyName);
    }

    // ロック処理（失敗回数のインクリメント・自動解除）は、その後 UnauthorizedException を
    // 投げてもロールバックされず確実に保存されるようにする。
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public LoginResponse login(HttpServletRequest request, LoginRequest body) {
        Long companyId = (Long) request.getAttribute(TenantResolutionInterceptor.ATTR_COMPANY_ID);
        String email = normalizeLower(body.getEmail());

        Optional<User> userOpt = email == null
                ? Optional.empty()
                : userRepository.findByCompany_IdAndEmail(companyId, email);

        if (userOpt.isEmpty()) {
            throw invalidCredentials();
        }

        User user = userOpt.get();
        autoUnlockIfExpired(user);

        if (STATUS_LOCKED.equals(user.getStatus())) {
            throw locked();
        }

        boolean passwordOk = STATUS_ACTIVE.equals(user.getStatus())
                && body.getPassword() != null
                && passwordEncoder.matches(body.getPassword(), user.getPassword());

        if (!passwordOk) {
            registerFailedAttempt(user);
            throw STATUS_LOCKED.equals(user.getStatus()) ? locked() : invalidCredentials();
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        return new LoginResponse(
                jwtService.issueAccessToken(user),
                jwtService.issueRefreshToken(user),
                "Bearer");
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
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
        User user = userRepository.findById(userId).orElseThrow(this::invalidToken);
        autoUnlockIfExpired(user);

        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            throw invalidToken();
        }

        return new LoginResponse(jwtService.issueAccessToken(user), refreshToken, "Bearer");
    }

    /** ロック期限を過ぎていれば ACTIVE へ戻し、失敗回数をリセットする。 */
    private void autoUnlockIfExpired(User user) {
        if (STATUS_LOCKED.equals(user.getStatus())
                && user.getLockedUntil() != null
                && !user.getLockedUntil().isAfter(LocalDateTime.now())) {
            user.setStatus(STATUS_ACTIVE);
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    /** 失敗回数を1増やし、しきい値に達したらロックする。 */
    private void registerFailedAttempt(User user) {
        int count = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(count);
        if (count >= authLockProperties.getMaxFailedAttempts()) {
            user.setStatus(STATUS_LOCKED);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(authLockProperties.getLockDurationMinutes()));
        }
        userRepository.save(user);
    }

    private UnauthorizedException invalidCredentials() {
        Locale locale = LocaleContextHolder.getLocale();
        return new UnauthorizedException(
                messageSource.getMessage("auth.error.invalid-credentials", null, locale));
    }

    private UnauthorizedException locked() {
        Locale locale = LocaleContextHolder.getLocale();
        return new UnauthorizedException(
                messageSource.getMessage("auth.error.locked", null, locale));
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
