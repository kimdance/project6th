package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * メニューのカテゴリ（例：刺身、揚げ物、ドリンク）。docs/04_architecture.md §4.4 の
 * menu_category テーブルに対応（FR-D02）。companyCode は非正規化コピー（FKは張らない。§3.1）。
 */
@Entity
@Table(name = "menu_category")
@Data
@EqualsAndHashCode(callSuper = true)
public class MenuCategory extends BaseEntity {

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

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
