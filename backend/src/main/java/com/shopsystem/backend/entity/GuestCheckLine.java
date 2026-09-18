package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 会計明細（どの注文明細をこの会計に割り当てたか）。docs/04_architecture.md §4.7 の
 * guest_check_line に対応（FR-G01・G11）。1つの order_line は同一 table_session 内で
 * ちょうど1つの guest_check にのみ割り当てられる（DBの UNIQUE(order_line_id)）。
 */
@Entity
@Table(name = "guest_check_line")
@Data
@EqualsAndHashCode(callSuper = true)
public class GuestCheckLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "check_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private GuestCheck check;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_line_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private OrderLine orderLine;

    /** 税込金額（unitPriceSnapJpy × quantity）。値引き前のこの明細の金額。 */
    @Column(name = "amount_jpy", nullable = false)
    private int amountJpy;

    @Column(nullable = false)
    private int quantity;
}
