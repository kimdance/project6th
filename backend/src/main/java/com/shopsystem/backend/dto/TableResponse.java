package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 卓（テーブル）のレスポンス（FR-B02）。qrToken はモバイルオーダー用QRの識別子。 */
@Data
@AllArgsConstructor
public class TableResponse {
    private Long id;
    private String tableNo;
    private int seatCount;
    private String area;
    private String qrToken;
    private String status;
    private boolean active;
}
