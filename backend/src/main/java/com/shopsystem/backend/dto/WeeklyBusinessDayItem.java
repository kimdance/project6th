package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/** 曜日ごとの営業日既定（FR-B07）。weekday は0（日曜）〜6（土曜）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyBusinessDayItem {
    private Integer weekday;
    private boolean open;
    private LocalTime openTime;
    private LocalTime closeTime;
    private Integer reservationCapacity;
}
