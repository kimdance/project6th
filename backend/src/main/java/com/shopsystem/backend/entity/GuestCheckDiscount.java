package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会計への値引き・クーポン・端数調整。docs/04_architecture.md §4.7 の guest_check_discount
 * に対応（FR-G02）。1会計に複数件持てる（都度追加。取消・変更は現状の運用では想定しない）。
 */
@Entity
@Table(name = "guest_check_discount")
@Data
@EqualsAndHashCode(callSuper = true)
public class GuestCheckDiscount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "check_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private GuestCheck check;

    /** AMOUNT（金額指定）／RATE（率指定）／COUPON／ROUNDING（端数調整） */
    @Column(nullable = false, length = 20)
    private String type;

    /** AMOUNT・ROUNDING・COUPONは円額、RATEは0〜100のパーセント値。 */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    /** 実際に会計から差し引かれた円額（RATE指定の場合も円換算後の値）。 */
    @Column(name = "amount_jpy", nullable = false)
    private int amountJpy;

    @Column(length = 255)
    private String reason;

    @Column(name = "applied_by", nullable = false, length = 255)
    private String appliedBy;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;
}
