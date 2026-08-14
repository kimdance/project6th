package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.entity.User;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final MessageSource messageSource;

    @Transactional
    public User registerUser(User user) {
        List<ErrorItem> errorItems = new ArrayList<>();

        // 1. パスワード桁数チェック
        if (user.getPassword() == null || user.getPassword().length() < 8) {
            String msg = messageSource.getMessage(
                "user.register.error.password-length",
                null,
                LocaleContextHolder.getLocale()
            );
            errorItems.add(new ErrorItem(msg, List.of("password")));
        }

        // 2. パスワード英数字混合チェック
        if (user.getPassword() != null && !user.getPassword().matches("^(?=.*[A-Za-z])(?=.*\\d).+$")) {
            String msg = messageSource.getMessage(
                "user.register.error.password-format",
                null,
                LocaleContextHolder.getLocale()
            );
            errorItems.add(new ErrorItem(msg, List.of("password")));
        }

        // 3. 電話番号ハイフンチェック
        if (user.getTelnumber() != null && user.getTelnumber().contains("-")) {
            String msg = messageSource.getMessage(
                "user.register.error.tel-hyphen",
                null,
                LocaleContextHolder.getLocale()
            );
            errorItems.add(new ErrorItem(msg, List.of("telnumber")));
        }

        // 4. 会社コード必須チェック
        if (user.getCompanyCode() == null || user.getCompanyCode().isBlank()) {
            String msg = messageSource.getMessage(
                "user.register.error.company-code-required",
                null,
                LocaleContextHolder.getLocale()
            );
            errorItems.add(new ErrorItem(msg, List.of("companyCode")));
        }

        // 5. 会社コードとメールアドレスの組み合わせ重複チェック
        if (user.getCompanyCode() != null && !user.getCompanyCode().isBlank()
                && userRepository.existsByCompanyCodeAndEmail(user.getCompanyCode(), user.getEmail())) {
            String duplicateMsg = messageSource.getMessage(
                "user.register.error.duplicate-company-email",
                null,
                LocaleContextHolder.getLocale()
            );
            errorItems.add(new ErrorItem(duplicateMsg, List.of("companyCode", "email")));
        }

        // ★ エラーが1件以上蓄積されていればスロー
        if (!errorItems.isEmpty()) {
            throw new BusinessException(errorItems);
        }

        return userRepository.save(user);
    }
}