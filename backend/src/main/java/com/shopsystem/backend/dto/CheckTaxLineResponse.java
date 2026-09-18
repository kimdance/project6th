package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CheckTaxLineResponse {
    private String taxCategory;
    private int taxableAmountJpy;
    private int taxAmountJpy;
}
