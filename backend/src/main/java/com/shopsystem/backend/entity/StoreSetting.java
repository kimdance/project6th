package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 店舗設定。docs/04_architecture.md §4.3 の store_setting テーブルに対応。
 * 主キーは store_id で store と 1:1（共有主キー）。
 */
@Entity
@Table(name = "store_setting")
@Data
@EqualsAndHashCode(callSuper = true)
public class StoreSetting extends BaseEntity {

    @Id
    @Column(name = "store_id")
    private Long storeId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "store_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Store store;

    /** FLOOR / CEIL / ROUND */
    @Column(name = "tax_rounding", nullable = false, length = 20)
    private String taxRounding = "FLOOR";

    @Column(name = "price_includes_tax", nullable = false)
    private boolean priceIncludesTax = true;

    @Column(name = "invoice_reg_no", length = 20)
    private String invoiceRegNo;

    /** APPROVAL / INSTANT */
    @Column(name = "web_reservation_mode", nullable = false, length = 20)
    private String webReservationMode = "APPROVAL";

    @Column(name = "mobile_order_enabled", nullable = false)
    private boolean mobileOrderEnabled = false;

    @Column(name = "last_order_default_min", nullable = false)
    private int lastOrderDefaultMin = 30;

    @Column(name = "cancel_charge_default_customer", nullable = false)
    private boolean cancelChargeDefaultCustomer = true;

    @Column(name = "cancel_charge_default_store", nullable = false)
    private boolean cancelChargeDefaultStore = false;

    /** 提供後の注文明細の取消（FR-E03）を店長承認必須にするか。既定falseはホールも取消可。 */
    @Column(name = "require_manager_approval_for_serve_cancel", nullable = false)
    private boolean requireManagerApprovalForServeCancel = false;

    /** 会計の取消・返金・値引き（FR-G10）を店長承認必須にするか。既定falseはホールも可。 */
    @Column(name = "require_manager_approval_for_void_refund", nullable = false)
    private boolean requireManagerApprovalForVoidRefund = false;
}
