package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 卓（テーブル）。docs/04_architecture.md §4.4 の dining_table テーブルに対応（FR-B02）。
 * qr_token はモバイルオーダー用QRの識別子。サーバがランダムに発番し、クライアントの自己申告は認めない。
 */
@Entity
@Table(name = "dining_table")
@Data
@EqualsAndHashCode(callSuper = true)
public class DiningTable extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Store store;

    @Column(name = "table_no", nullable = false, length = 20)
    private String tableNo;

    @Column(name = "seat_count", nullable = false)
    private int seatCount;

    @Column(length = 100)
    private String area;

    @Column(name = "qr_token", nullable = false, length = 64)
    private String qrToken;

    /** EMPTY / OCCUPIED / BILLING */
    @Column(nullable = false, length = 20)
    private String status = "EMPTY";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
