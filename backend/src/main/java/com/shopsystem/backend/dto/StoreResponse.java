package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** POST /api/v1/stores のレスポンス。 */
@Data
@AllArgsConstructor
public class StoreResponse {
    private Long id;
    private String name;
    private String address;
    private String phone;
    private String businessHours;
    private int seatCount;
    private String timezone;
    private boolean active;
}
