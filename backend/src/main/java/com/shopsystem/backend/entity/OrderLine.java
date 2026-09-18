package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 注文明細。docs/04_architecture.md §4.6 の order_line テーブルに対応（FR-E02・E03・E03b・
 * E03c・E07。03_domain_model.md §4.5）。fire_state（HELD/FIRED）・registered_at_device_raw・
 * time_low_confidence はオフライン同期（04 §9）向けの列で、このエンティティ（オンライン専用の
 * 実装）では fireState は常に既定FIRED、それ以外は常にnull/falseのまま扱う。
 */
@Entity
@Table(name = "order_line")
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_session_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TableSession tableSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private MenuItem menuItem;

    @Column(name = "item_name_snap", nullable = false, length = 255)
    private String itemNameSnap;

    @Column(name = "unit_price_snap_jpy", nullable = false)
    private int unitPriceSnapJpy;

    @Column(name = "tax_category_snap", nullable = false, length = 20)
    private String taxCategorySnap;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 500)
    private String note;

    /** PENDING / PREPARING / SERVED / CANCELLED / REJECTED（03_domain_model.md §4.5） */
    @Column(name = "serve_status", nullable = false, length = 20)
    private String serveStatus = "PENDING";

    /** HELD / FIRED。フェーズ1のこのエンティティでは常に既定のFIRED（後出し保留は未実装）。 */
    @Column(name = "fire_state", nullable = false, length = 20)
    private String fireState = "FIRED";

    @Column(name = "fired_at")
    private LocalDateTime firedAt;

    @Column(name = "fired_by", length = 255)
    private String firedBy;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "registered_by", nullable = false, length = 255)
    private String registeredBy;

    @Column(name = "registered_at_device_raw")
    private LocalDateTime registeredAtDeviceRaw;

    /** 注文営業日。フェーズ1は registeredAt の日付をそのまま使う簡易実装（04 §9の営業日境界計算は未実装）。 */
    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "time_low_confidence", nullable = false)
    private boolean timeLowConfidence = false;

    @Column(name = "served_at")
    private LocalDateTime servedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by", length = 255)
    private String cancelledBy;

    /** ORDER_MISTAKE / QUALITY / DELAY / WRONG_SERVE / SOLD_OUT / CUSTOMER / OTHER */
    @Column(name = "cancel_reason", length = 20)
    private String cancelReason;

    @Column(name = "cancel_chargeable")
    private Boolean cancelChargeable;

    @Column(name = "was_cooked")
    private Boolean wasCooked;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "remake_of_line_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private OrderLine remakeOfLine;

    @Column(name = "client_ref_id", length = 40)
    private String clientRefId;
}
