package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.AuditLogPageResponse;
import com.shopsystem.backend.service.AuditLogService;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 監査ログの検索・閲覧（FR-J04）。経営管理者のみ（権限チェックはサービス層で行う）。
 * ログイン済み前提（{@code JwtAuthenticationInterceptor} が事前に認証・テナント文脈を確立する）。
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public AuditLogPageResponse search(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return auditLogService.search(storeId, action, actor, from, to, page, size);
    }
}
