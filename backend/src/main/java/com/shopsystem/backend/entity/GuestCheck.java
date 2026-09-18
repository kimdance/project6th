package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会計（チェック）。docs/04_architecture.md §4.7 の guest_check テーブルに対応
 * （FR-G01。03_domain_model.md §4.6の状態遷移）。
 * 税額計算の簡易方針（フェーズ1）：メニュー価格は税込（店舗設定 price_includes_tax=true 前提）
 * のため、税区分ごとの税込小計から税率で逆算し、税抜小計（subtotalJpy）と税額
 * （taxTotalJpy）に分解する。値引きは税抜小計に対して適用し、
 * total = subtotal - discountTotal + taxTotal（03_domain_model.md §4.6 不変条件）。
 * レシート／領収書（receipt。FR-G08・G09）は後続で対応するため、このエンティティでは扱わない。
 */
@Entity
@Table(name = "guest_check")
@Data
@EqualsAndHashCode(callSuper = true)
public class GuestCheck extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_session_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TableSession tableSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Store store;

    @Column(name = "seq_in_session", nullable = false)
    private int seqInSession;

    /** OPEN / FINALIZED / VOIDED（03_domain_model.md §4.6） */
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "subtotal_jpy", nullable = false)
    private int subtotalJpy;

    @Column(name = "discount_total_jpy", nullable = false)
    private int discountTotalJpy;

    @Column(name = "tax_total_jpy", nullable = false)
    private int taxTotalJpy;

    @Column(name = "total_jpy", nullable = false)
    private int totalJpy;

    /** NONE / BY_HEAD / BY_ITEM。フェーズ1は別会計（BY_ITEM相当の運用）のみ実際に機能する。 */
    @Column(name = "split_type", nullable = false, length = 20)
    private String splitType = "NONE";

    @Column(name = "split_count")
    private Integer splitCount;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "finalized_by", length = 255)
    private String finalizedBy;
}
