package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CheckLineResponse {
    private Long id;
    private Long orderLineId;
    private String itemNameSnap;
    private int quantity;
    private int amountJpy;
}
