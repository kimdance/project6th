package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 営業日・臨時休業。docs/04_architecture.md §4.4 の store_business_day に対応（FR-B07）。
 * businessDate（特定日の上書き）と weekday（曜日の既定）はどちらか一方のみ設定する
 * （DB の ck_store_business_day_date_xor_weekday で強制）。
 */
@Entity
@Table(name = "store_business_day")
@Data
@EqualsAndHashCode(callSuper = true)
public class StoreBusinessDay extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Store store;

    /** 特定日の上書き（臨時休業・特別営業）。weekday と排他。 */
    @Column(name = "business_date")
    private LocalDate businessDate;

    /** 曜日の既定（0=日曜〜6=土曜）。businessDate と排他。DB列は SMALLINT。 */
    @Column
    private Short weekday;

    @Column(name = "is_open", nullable = false)
    private boolean open = true;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @Column(name = "reservation_capacity")
    private Integer reservationCapacity;
}
