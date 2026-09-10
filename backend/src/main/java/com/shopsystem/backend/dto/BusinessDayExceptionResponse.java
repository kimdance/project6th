package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/** 特定日の営業日上書き（臨時休業・特別営業）のレスポンス（FR-B07）。 */
@Data
@AllArgsConstructor
public class BusinessDayExceptionResponse {
    private Long id;
    private LocalDate businessDate;
    private boolean open;
    private LocalTime openTime;
    private LocalTime closeTime;
    private Integer reservationCapacity;
}
