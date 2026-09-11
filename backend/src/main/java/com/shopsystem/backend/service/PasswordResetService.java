package com.shopsystem.backend.service;

import com.shopsystem.backend.config.PasswordResetProperties;
import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.PasswordResetConfirmRequest;
import com.shopsystem.backend.dto.PasswordResetRequest;
import com.shopsystem.backend.entity.PasswordResetToken;
import com.shopsystem.backend.entity.User;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.repository.PasswordResetTokenRepository;
import com.shopsystem.backend.repository.UserRepository;
import com.shopsystem.backend.web.TenantResolutionInterceptor;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * パスワードを忘れた場合のリセット（FR-A04・EXT-03）。メール本文には生トークンを載せ、
 * DBにはSHA-256ハッシュだけを保存する（漏えい時に生トークンとして使い回されないように）。
 * メールアドレスの登録有無は、テナントの存在有無と同様に外部へ漏らさない
 * （{@link #requestReset}は該当有無にかかわらず常に同じ結果になる）。
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern PASSWORD_ALNUM = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).+$");
    private static final int PASSWORD_MIN = 8;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final PasswordResetProperties properties;
    private final MessageSource messageSource;

    /**
     * リセットメールの送信を申し込む。会社は未認証のためHostヘッダ由来のセッション（
     * {@link TenantResolutionInterceptor}）から解決する。メールアドレスが見つからない場合も
     * 例外を投げず、見つかった場合と同じように処理を終える（列挙攻撃対策）。
     */
    @Transactional
    public void requestReset(HttpServletRequest request, PasswordResetRequest body) {
        Long companyId = (Long) request.getAttribute(TenantResolutionInterceptor.ATTR_COMPANY_ID);
        String companyCode = (String) request.getAttribute(TenantResolutionInterceptor.ATTR_COMPANY_CODE);
        String email = normalizeLower(body.getEmail());

        if (email == null) {
            return;
        }
        userRepository.findByCompany_IdAndEmail(companyId, email)
                .ifPresent(user -> issueAndSendToken(user, companyCode));
    }

    private void issueAndSendToken(User user, String companyCode) {
        LocalDateTime now = LocalDateTime.now();
        // 既存の未使用トークンは失効させる（有効なリンクが複数残らないように）。
        tokenRepository.findByUser_IdAndUsedAtIsNull(user.getId()).forEach(existing -> {
            existing.setUsedAt(now);
            tokenRepository.save(existing);
        });

        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(sha256Hex(rawToken));
        token.setExpiresAt(now.plusMinutes(properties.getExpiryMinutes()));
        tokenRepository.save(token);

        String resetUrl = String.format(properties.getFrontendBaseUrlTemplate(), companyCode)
                + "/reset-password?token=" + rawToken;
        sendEmail(user.getEmail(), resetUrl);
    }

    private void sendEmail(String to, String resetUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMailFrom());
        message.setTo(to);
        message.setSubject("【店舗管理システム】パスワード再設定のご案内");
        message.setText(
                "パスワードを再設定するには、以下のリンクから手続きしてください。\n\n"
                        + resetUrl + "\n\n"
                        + "このリンクの有効期限は" + properties.getExpiryMinutes() + "分です。\n"
                        + "このメールに心当たりがない場合は、破棄してください（パスワードは変更されません）。");
        mailSender.send(message);
    }

    /** リンクのトークンと新しいパスワードから、実際にパスワードを更新する。 */
    @Transactional
    public void confirmReset(PasswordResetConfirmRequest body) {
        Locale locale = LocaleContextHolder.getLocale();
        List<ErrorItem> errors = new ArrayList<>();

        String rawToken = body.getToken();
        String password = body.getPassword();

        if (rawToken == null || rawToken.isBlank()) {
            errors.add(err(locale, "password-reset.error.token.invalid", "token"));
        }
        if (password == null || password.length() < PASSWORD_MIN) {
            errors.add(err(locale, "password-reset.error.password.length", "password"));
        } else if (!PASSWORD_ALNUM.matcher(password).matches()) {
            errors.add(err(locale, "password-reset.error.password.format", "password"));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }

        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken token = tokenRepository.findByTokenHash(sha256Hex(rawToken))
                .filter(t -> t.getUsedAt() == null)
                .filter(t -> t.getExpiresAt().isAfter(now))
                .orElse(null);
        if (token == null) {
            throw new BusinessException(List.of(err(locale, "password-reset.error.token.invalid", "token")));
        }

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(password));
        // パスワードを再設定できた時点で本人確認が取れているため、ロック状態も合わせて解除する（FR-A08）。
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        if ("LOCKED".equals(user.getStatus())) {
            user.setStatus("ACTIVE");
        }
        userRepository.save(user);

        token.setUsedAt(now);
        tokenRepository.save(token);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private ErrorItem err(Locale locale, String code, String field) {
        return new ErrorItem(messageSource.getMessage(code, null, locale), List.of(field));
    }

    private static String normalizeLower(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t.toLowerCase(Locale.ROOT);
    }
}
