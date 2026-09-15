package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.TenantProvisioningRequest;
import com.shopsystem.backend.entity.Company;
import com.shopsystem.backend.entity.User;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.exception.ConflictException;
import com.shopsystem.backend.repository.CompanyRepository;
import com.shopsystem.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * テナント作成。company と最初の users（OWNER）を1トランザクションで作成する。
 * フェーズ1では運営者専用（呼び出し側で合言葉を検証する。04_architecture.md §6.1）。
 * company_code はサブドメインのラベルに使うため DNS ラベル安全な形式に限定する（§3.1）。
 */
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    /** 先頭・末尾・連続のハイフン不可。照合は小文字前提。 */
    private static final Pattern COMPANY_CODE = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int COMPANY_CODE_MIN = 3;
    private static final int COMPANY_CODE_MAX = 63;

    /** サブドメイン運用と衝突するため発番不可。必要に応じて追加する。 */
    private static final Set<String> RESERVED_COMPANY_CODES = Set.of(
            "www", "api", "accounts", "admin", "app", "auth", "login", "signup",
            "mail", "smtp", "static", "assets", "cdn", "status", "help", "support",
            "dev", "staging", "test", "demo", "pos", "guest", "kds", "internal", "public"
    );

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** 英字と数字の両方を含む（旧 UserService のポリシーを踏襲）。 */
    private static final Pattern PASSWORD_ALNUM = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).+$");
    private static final int PASSWORD_MIN = 8;

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MessageSource messageSource;

    @Transactional
    public Company provision(TenantProvisioningRequest req) {
        Locale locale = LocaleContextHolder.getLocale();
        List<ErrorItem> errors = new ArrayList<>();

        String companyCode = normalizeLower(req.getCompanyCode());
        String companyName = trimToNull(req.getCompanyName());
        String ownerName = trimToNull(req.getOwnerName());
        String ownerEmail = normalizeLower(req.getOwnerEmail());
        String password = req.getPassword();

        // --- company_code ---
        if (companyCode == null) {
            errors.add(err(locale, "tenant.error.company-code.required", "companyCode"));
        } else if (!isValidCompanyCode(companyCode)) {
            errors.add(err(locale, "tenant.error.company-code.format", "companyCode"));
        } else if (RESERVED_COMPANY_CODES.contains(companyCode)) {
            errors.add(err(locale, "tenant.error.company-code.reserved", "companyCode"));
        }

        // --- 会社名・氏名 ---
        if (companyName == null) {
            errors.add(err(locale, "tenant.error.company-name.required", "companyName"));
        }
        if (ownerName == null) {
            errors.add(err(locale, "tenant.error.owner-name.required", "ownerName"));
        }

        // --- メールアドレス ---
        if (ownerEmail == null) {
            errors.add(err(locale, "tenant.error.owner-email.required", "ownerEmail"));
        } else if (!EMAIL.matcher(ownerEmail).matches()) {
            errors.add(err(locale, "tenant.error.owner-email.format", "ownerEmail"));
        }

        // --- パスワード ---
        if (password == null || password.length() < PASSWORD_MIN) {
            errors.add(err(locale, "tenant.error.password.length", "password"));
        } else if (!PASSWORD_ALNUM.matcher(password).matches()) {
            errors.add(err(locale, "tenant.error.password.format", "password"));
        }

        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }

        // 形式チェックを通過してから一意性を確認する（409）。事前チェックと登録の間の競合は
        // DB の UNIQUE 制約が最終防衛線となるため、saveAndFlush で即座に検出して409へ変換する。
        if (companyRepository.existsByCompanyCode(companyCode)) {
            throw new ConflictException(
                    messageSource.getMessage("tenant.error.company-code.duplicate", null, locale));
        }

        Company company = new Company();
        company.setCompanyCode(companyCode);
        company.setName(companyName);
        company.setContractStatus("ACTIVE");
        try {
            company = companyRepository.saveAndFlush(company);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    messageSource.getMessage("tenant.error.company-code.duplicate", null, locale));
        }

        User owner = new User();
        owner.setCompany(company);
        // 店舗の割り当ては未設定（全店＝本部）のまま。
        owner.setName(ownerName);
        owner.setEmail(ownerEmail);
        owner.setPassword(passwordEncoder.encode(password));
        owner.setRole("OWNER");
        owner.setStatus("ACTIVE");
        owner.setTwoFactorEnabled(false);
        userRepository.save(owner);

        return company;
    }

    private boolean isValidCompanyCode(String code) {
        return code.length() >= COMPANY_CODE_MIN
                && code.length() <= COMPANY_CODE_MAX
                && COMPANY_CODE.matcher(code).matches();
    }

    private ErrorItem err(Locale locale, String code, String field) {
        return new ErrorItem(messageSource.getMessage(code, null, locale), List.of(field));
    }

    private static String normalizeLower(String s) {
        String t = trimToNull(s);
        return t == null ? null : t.toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
