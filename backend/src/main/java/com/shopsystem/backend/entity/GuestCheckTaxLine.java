package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 税区分ごとの内訳。docs/04_architecture.md §4.7 の guest_check_tax_line に対応（FR-G01・G09）。
 * 1会計につき税区分（STANDARD_10／REDUCED_8）ごとに1行（インボイス制度の「1請求書につき
 * 税率ごとに1回」端数処理に対応するため、会計を再計算するたびに全行を作り直す）。
 */
@Entity
@Table(name = "guest_check_tax_line")
@Data
@EqualsAndHashCode(callSuper = true)
public class GuestCheckTaxLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "check_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private GuestCheck check;

    /** STANDARD_10（10%）／REDUCED_8（8%） */
    @Column(name = "tax_category", nullable = false, length = 20)
    private String taxCategory;

    /** 税抜対象額（値引き後）。 */
    @Column(name = "taxable_amount_jpy", nullable = false)
    private int taxableAmountJpy;

    @Column(name = "tax_amount_jpy", nullable = false)
    private int taxAmountJpy;
}
