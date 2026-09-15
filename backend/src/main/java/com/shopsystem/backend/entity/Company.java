package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * テナント（企業）。docs/04_architecture.md §4.3 の company テーブルに対応。
 *
 * company_code は URL サブドメインのラベルとして使う自然キー（§3.1 / §6.1）。
 * 形式（小文字英数字とハイフン・3〜63文字）は DB の ck_company_code_format 制約で強制し、
 * 予約語の拒否はアプリ層で行う。
 */
@Entity
@Table(name = "company")
@Data
@EqualsAndHashCode(callSuper = true)
public class Company extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_code", nullable = false, unique = true, length = 63)
    private String companyCode;

    @Column(nullable = false)
    private String name;

    /** ACTIVE / SUSPENDED / CANCELLED */
    @Column(name = "contract_status", nullable = false, length = 30)
    private String contractStatus = "ACTIVE";
}
