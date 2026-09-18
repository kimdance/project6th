package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 決済。docs/04_architecture.md §4.7 の payment テーブルに対応（FR-G03・G05・G06・G07・G07b）。
 * フェーズ1は実際の決済代行連携（PayPay加盟店API／クレジットカード決済代行SDK）を行わず、
 * すべての決済手段を「スタッフが金額を確認して記録する」手入力方式で扱う（04_architecture.md
 * 追補を参照。決済代行サービスの契約自体が `02_requirements.md` §11.1 のとおり経営判断待ちのため）。
 * このためこのエンティティでは isManualEntry は常にtrue、statusは即時SUCCESSまたはFAILEDのみで、
 * externalTxnId／Webhook経由のPENDING解決は扱わない。
 */
@Entity
@Table(name = "payment")
@Data
@EqualsAndHashCode(callSuper = true)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "check_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private GuestCheck check;

    /** CASH / PAYPAY / CREDIT_CARD / RAKUTEN_PAY */
    @Column(name = "method_type", nullable = false, length = 20)
    private String methodType;

    @Column(name = "amount_jpy", nullable = false)
    private int amountJpy;

    /** SUCCESS / FAILED / PENDING。フェーズ1（手入力方式）は常にSUCCESS。 */
    @Column(nullable = false, length = 20)
    private String status = "SUCCESS";

    @Column(name = "external_txn_id", length = 100)
    private String externalTxnId;

    /** 現金のみ：預り金。 */
    @Column(name = "tendered_jpy")
    private Integer tenderedJpy;

    /** 現金のみ：釣り銭。 */
    @Column(name = "change_jpy")
    private Integer changeJpy;

    @Column(name = "is_manual_entry", nullable = false)
    private boolean manualEntry = true;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by", nullable = false, length = 255)
    private String processedBy;
}
