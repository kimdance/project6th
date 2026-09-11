package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.UserSummaryResponse;
import com.shopsystem.backend.dto.UserUpdateRequest;
import com.shopsystem.backend.entity.Store;
import com.shopsystem.backend.entity.User;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.exception.ForbiddenException;
import com.shopsystem.backend.exception.NotFoundException;
import com.shopsystem.backend.repository.StoreRepository;
import com.shopsystem.backend.repository.UserRepository;
import com.shopsystem.backend.web.TenantContext;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * ユーザー管理（一覧・役割／所属店舗の変更）。ユーザー登録画面（FR-A03）は役割を
 * 「スタッフ（HALL）」「アルバイト（PARTTIME）」に限定し、店舗も選ばせないため、
 * 店長・経営管理者への昇格と店舗の割り当ては、この画面から経営管理者だけが行える
 * （`02_requirements.md` §3.2 権限マトリクス「ユーザーの権限変更」）。
 */
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private static final Set<String> VALID_ROLES = Set.of("OWNER", "MANAGER", "HALL", "KITCHEN", "PARTTIME");

    /**
     * この画面から管理者が直接指定できる状態。LOCKED はログイン失敗回数による自動ロック
     * （FR-A08）専用の内部状態のため、ここからは指定させない（誤って早期解除する事故を防ぐ）。
     */
    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "RETIRED");

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final MessageSource messageSource;

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> list() {
        requireOwner();
        Long companyId = TenantContext.get().companyId();
        return userRepository.findByCompany_IdOrderById(companyId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public UserSummaryResponse update(Long userId, UserUpdateRequest req) {
        requireOwner();
        Long companyId = TenantContext.get().companyId();
        Locale locale = LocaleContextHolder.getLocale();

        User user = userRepository.findByIdAndCompany_Id(userId, companyId).orElseThrow(this::notFound);

        String role = req.getRole();
        if (role == null || !VALID_ROLES.contains(role)) {
            throw new BusinessException(List.of(err(locale, "user.error.role.invalid", "role")));
        }

        String status = req.getStatus();
        if (status == null || !VALID_STATUSES.contains(status)) {
            throw new BusinessException(List.of(err(locale, "user.error.status.invalid", "status")));
        }

        // 経営管理者（OWNER）が1人もいなくなる変更（役割を外す・退職にする）は、テナントを
        // 操作不能にしてしまうため拒否する。
        boolean losesOwner = "OWNER".equals(user.getRole())
                && (!"OWNER".equals(role) || "RETIRED".equals(status));
        if (losesOwner && userRepository.countByCompany_IdAndRole(companyId, "OWNER") <= 1) {
            throw new BusinessException(List.of(err(locale, "user.error.last-owner", "role")));
        }

        Store store = null;
        if (req.getStoreId() != null) {
            store = storeRepository.findByIdAndCompany_Id(req.getStoreId(), companyId)
                    .orElseThrow(() -> new NotFoundException(
                            messageSource.getMessage("store.error.not-found", null, locale)));
        }

        user.setRole(role);
        user.setStore(store);
        user.setStatus(status);
        if ("RETIRED".equals(status)) {
            // ログイン失敗回数によるロック（FR-A08）の解除サイクルで、退職済みアカウントが
            // 意図せず ACTIVE に戻ってしまわないよう、退職と同時にリセットしておく。
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
        }
        userRepository.save(user);

        return toSummary(user);
    }

    private UserSummaryResponse toSummary(User user) {
        Store store = user.getStore();
        return new UserSummaryResponse(
                user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getStatus(),
                store != null ? store.getId() : null,
                store != null ? store.getName() : null);
    }

    private void requireOwner() {
        if (!"OWNER".equals(TenantContext.get().role())) {
            throw forbidden();
        }
    }

    private ForbiddenException forbidden() {
        return new ForbiddenException(
                messageSource.getMessage("user.error.forbidden", null, LocaleContextHolder.getLocale()));
    }

    private NotFoundException notFound() {
        return new NotFoundException(
                messageSource.getMessage("user.error.not-found", null, LocaleContextHolder.getLocale()));
    }

    private ErrorItem err(Locale locale, String code, String field) {
        return new ErrorItem(messageSource.getMessage(code, null, locale), List.of(field));
    }
}
