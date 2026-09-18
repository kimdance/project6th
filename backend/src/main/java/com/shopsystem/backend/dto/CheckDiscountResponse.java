package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class CheckDiscountResponse {
    private Long id;
    private String type;
    private BigDecimal value;
    private int amountJpy;
    private String reason;
    private String appliedBy;
    private LocalDateTime appliedAt;
}
