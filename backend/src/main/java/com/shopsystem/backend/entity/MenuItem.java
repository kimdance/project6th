package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalTime;

/**
 * メニュー項目。docs/04_architecture.md §4.4 の menu_item テーブルに対応（FR-D01・D03）。
 * companyCode は非正規化コピー（FKは張らない。§3.1）。availableFrom/availableTo（期間限定。
 * FR-D04）と menu_option_group/menu_option（トッピング等。FR-D05）はフェーズ1未実装のため、
 * 列自体はスキーマにあるがこのエンティティ／APIでは扱わない。
 */
@Entity
@Table(name = "menu_item")
@Data
@EqualsAndHashCode(callSuper = true)
public class MenuItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_code", nullable = false, length = 63)
    private String companyCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private MenuCategory category;

    /** COOK（調理あり／KDS対象）／NO_COOK（調理なし。ドリンクの瓶出し等。KDS対象外。§9） */
    @Column(name = "prep_type", nullable = false, length = 20)
    private String prepType = "COOK";

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "price_jpy", nullable = false)
    private int priceJpy;

    /** STANDARD_10（標準10%）／REDUCED_8（軽減税率8%） */
    @Column(name = "tax_category", nullable = false, length = 20)
    private String taxCategory;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "serve_time_from")
    private LocalTime serveTimeFrom;

    @Column(name = "serve_time_to")
    private LocalTime serveTimeTo;

    /** ON_SALE（販売中）／SOLD_OUT（売り切れ）／SUSPENDED（提供停止）。FR-D03。 */
    @Column(name = "sales_status", nullable = false, length = 20)
    private String salesStatus = "ON_SALE";

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
