package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/** GET /api/v1/audit-logs のレスポンス1件（FR-J04）。 */
@Data
@AllArgsConstructor
public class AuditLogResponse {
    private Long id;
    private Long storeId;
    private String storeName;
    private String actor;
    private String action;
    private String targetType;
    private Long targetId;
    private String beforeSummary;
    private String afterSummary;
    private String ip;
    private String device;
    private LocalDateTime occurredAt;
}
