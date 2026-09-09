package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 店舗。docs/04_architecture.md §4.3 の store テーブルに対応。
 * company から1ホップのため company_code の非正規化コピーは持たない（§3.1）。
 */
@Entity
@Table(name = "store")
@Data
@EqualsAndHashCode(callSuper = true)
public class Store extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Company company;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String address;

    @Column(length = 30)
    private String phone;

    @Column(name = "business_hours", length = 255)
    private String businessHours;

    @Column(name = "seat_count", nullable = false)
    private int seatCount;

    @Column(nullable = false, length = 50)
    private String timezone = "Asia/Tokyo";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
