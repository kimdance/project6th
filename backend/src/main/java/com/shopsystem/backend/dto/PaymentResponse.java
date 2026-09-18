package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private String methodType;
    private int amountJpy;
    private String status;
    private Integer tenderedJpy;
    private Integer changeJpy;
    private LocalDateTime processedAt;
    private String processedBy;
}
