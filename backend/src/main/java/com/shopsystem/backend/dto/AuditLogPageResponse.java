package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** GET /api/v1/audit-logs のページ応答。 */
@Data
@AllArgsConstructor
public class AuditLogPageResponse {
    private List<AuditLogResponse> content;
    private long totalElements;
    private int page;
    private int size;
}
