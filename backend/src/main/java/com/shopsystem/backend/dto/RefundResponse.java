package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class RefundResponse {
    private Long id;
    private Long paymentId;
    private int amountJpy;
    private String reason;
    private String reasonNote;
    private String executedBy;
    private LocalDateTime executedAt;
    private String approvedBy;
}
