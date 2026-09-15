package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** GET /api/v1/stores/{storeId}/business-days のレスポンス（FR-B07）。 */
@Data
@AllArgsConstructor
public class BusinessDaysResponse {
    private List<WeeklyBusinessDayItem> weekly;
    private List<BusinessDayExceptionResponse> exceptions;
}
