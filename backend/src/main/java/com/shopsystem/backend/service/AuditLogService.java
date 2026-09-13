package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.AuditLogPageResponse;
import com.shopsystem.backend.dto.AuditLogResponse;
import com.shopsystem.backend.entity.AuditLog;
import com.shopsystem.backend.exception.ForbiddenException;
import com.shopsystem.backend.repository.AuditLogRepository;
import com.shopsystem.backend.repository.StoreRepository;
import com.shopsystem.backend.repository.UserRepository;
import com.shopsystem.backend.web.TenantContext;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 監査ログの記録・検索（FR-J01〜FR-J04）。テーブルは V1__init_schema.sql で作成済みで、
 * 追記専用（更新・削除のAPIは提供しない。FR-J03）。
 *
 * ログイン・自己登録・パスワード再設定は認証確立前（{@link TenantContext} がまだ無い）ため、
 * companyCode・actor を呼び出し元が明示する {@link #record} を使う。ログイン後の操作は
 * {@link TenantContext} から自動的に補う {@link #recordForCurrentUser} を使う。
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository auditLogRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final MessageSource messageSource;

    /** ログイン中ユーザーによる操作を記録する（storeId は店舗に紐づかない操作なら null）。 */
    @Transactional
    public void recordForCurrentUser(String action, Long storeId, String targetType, Long targetId,
            String beforeSummary, String afterSummary) {
        TenantContext.Data ctx = TenantContext.get();
        String actor = userRepository.findById(ctx.userId())
                .map(u -> u.getEmail())
                .orElse("user:" + ctx.userId());
        record(ctx.companyCode(), storeId, actor, action, targetType, targetId, beforeSummary, afterSummary);
    }

    /** 認証確立前（ログイン・自己登録・パスワード再設定）の操作を記録する。 */
    @Transactional
    public void record(String companyCode, Long storeId, String actor, String action,
            String targetType, Long targetId, String beforeSummary, String afterSummary) {
        AuditLog log = new AuditLog();
        log.setCompanyCode(companyCode);
        if (storeId != null) {
            log.setStore(storeRepository.getReferenceById(storeId));
        }
        log.setActor(actor);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setBeforeSummary(beforeSummary);
        log.setAfterSummary(afterSummary);
        log.setIp(currentIp());
        log.setDevice(currentDevice());
        auditLogRepository.save(log);
    }

    /**
     * FR-J04：経営管理者（全店）・店長（自店のみ）による検索・閲覧。期間・実行者・操作種別・
     * 店舗で絞り込める。店長は自分の所属店舗に紐づく操作のみ閲覧でき、store_id が null の
     * 全社共通操作（ログイン・パスワード変更・ユーザー権限変更等）は対象外（店舗設定の権限
     * マトリクスと同じ「オーナー＝全店、店長＝自店のみ」の考え方に合わせた）。
     */
    @Transactional(readOnly = true)
    public AuditLogPageResponse search(Long storeId, String action, String actor,
            LocalDateTime from, LocalDateTime to, int page, int size) {
        TenantContext.Data ctx = TenantContext.get();
        boolean isOwner = "OWNER".equals(ctx.role());
        boolean isManager = "MANAGER".equals(ctx.role());
        if (!isOwner && !isManager) {
            throw forbidden();
        }

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(safePage, safeSize);

        Page<AuditLog> result;
        if (isOwner) {
            result = auditLogRepository.search(ctx.companyCode(), storeId, action, actor, from, to, pageable);
        } else {
            Set<Long> allowedStoreIds = ctx.storeIds();
            if (storeId != null && !allowedStoreIds.contains(storeId)) {
                throw forbidden();
            }
            if (allowedStoreIds.isEmpty()) {
                return new AuditLogPageResponse(List.of(), 0, safePage, safeSize);
            }
            result = auditLogRepository.searchRestrictedToStores(
                    ctx.companyCode(), allowedStoreIds, storeId, action, actor, from, to, pageable);
        }

        List<AuditLogResponse> content = result.getContent().stream().map(this::toResponse).toList();
        return new AuditLogPageResponse(content, result.getTotalElements(), safePage, safeSize);
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getStore() != null ? log.getStore().getId() : null,
                log.getStore() != null ? log.getStore().getName() : null,
                log.getActor(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getBeforeSummary(),
                log.getAfterSummary(),
                log.getIp(),
                log.getDevice(),
                log.getOccurredAt());
    }

    private static String currentIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String currentDevice() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getHeader("User-Agent");
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes servletAttrs ? servletAttrs.getRequest() : null;
    }

    private ForbiddenException forbidden() {
        return new ForbiddenException(
                messageSource.getMessage("audit-log.error.forbidden", null, LocaleContextHolder.getLocale()));
    }
}
