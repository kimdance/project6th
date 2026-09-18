package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 返金。docs/04_architecture.md §4.7 の refund テーブルに対応（FR-G10）。
 * 会計確定（FINALIZED）後の訂正のみに使う（確定前の取消は check.status=VOIDED を使う）。
 * check 本体（金額・明細）は不変で、返金イベントを追加するのみ。
 */
@Entity
@Table(name = "refund")
@Data
@EqualsAndHashCode(callSuper = true)
public class Refund extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "check_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private GuestCheck check;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Payment payment;

    @Column(name = "amount_jpy", nullable = false)
    private int amountJpy;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "reason_note", length = 500)
    private String reasonNote;

    @Column(name = "executed_by", nullable = false, length = 255)
    private String executedBy;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;
}
