package com.shopsystem.backend.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/** POST /api/v1/stores/{storeId}/business-days/exceptions のリクエストボディ（FR-B07）。 */
@Data
public class BusinessDayExceptionRequest {
    private LocalDate businessDate;
    private boolean open;
    private LocalTime openTime;
    private LocalTime closeTime;
    private Integer reservationCapacity;
}
