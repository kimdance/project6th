package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 税率の変更履歴。docs/04_architecture.md の tax_rate テーブルに対応（FR-B03）。
 * taxCategory（STANDARD_10／REDUCED_8）は区分のコードのまま変更せず、実際の
 * パーセンテージ（ratePercent）だけを店舗ごとに編集できるようにする。終了日は持たず、
 * 同じ区分の中で effectiveFrom が最も新しい（今日以前の）行がそのとき有効な税率になる。
 */
@Entity
@Table(name = "tax_rate")
@Data
@EqualsAndHashCode(callSuper = true)
public class TaxRate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Store store;

    /** STANDARD_10（標準税率）／REDUCED_8（軽減税率）。区分のコード自体は変更しない。 */
    @Column(name = "tax_category", nullable = false, length = 20)
    private String taxCategory;

    @Column(name = "rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal ratePercent;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;
}
