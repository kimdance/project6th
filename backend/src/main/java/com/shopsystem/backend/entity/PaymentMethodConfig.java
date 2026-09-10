package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 決済手段の有効化・接続情報。docs/04_architecture.md §4.4 の payment_method_config に対応
 * （FR-B04・FR-B05）。credentialEnc は暗号化済みバイト列のみを保持し、平文は保存しない。
 */
@Entity
@Table(name = "payment_method_config")
@Data
@EqualsAndHashCode(callSuper = true)
public class PaymentMethodConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Store store;

    /** CASH / PAYPAY / CREDIT_CARD / RAKUTEN_PAY */
    @Column(name = "method_type", nullable = false, length = 20)
    private String methodType;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "display_name", length = 100)
    private String displayName;

    /** 暗号化済みの接続情報（PayPay加盟店情報・クレカ決済代行のキー等）。平文は保持しない。 */
    @Column(name = "credential_enc")
    private byte[] credentialEnc;

    @Column(length = 500)
    private String note;
}
