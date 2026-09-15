package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * ログインアカウント。docs/04_architecture.md §4.3 の users テーブルに対応。
 *
 * テナントは company_id FK で表す（company_code 列は持たない。§3.1）。
 * 一意キーは company_id + email。ログイン時の company_id はURLサブドメイン由来の
 * セッションから解決する（§6.1）。人事・労務の対象者は staff 側で表す（§4.9）。
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_users_company_id_email",
        columnNames = { "company_id", "email" }
    )
)
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Company company;

    /** 空 = 全店（本部ユーザー等）。1人が複数店舗を兼任できる（V9）。 */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_store",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "store_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Store> stores = new HashSet<>();

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String telnumber;

    /** OWNER / MANAGER / HALL / KITCHEN / PARTTIME（認可に用いる権限） */
    @Column(nullable = false, length = 20)
    private String role = "OWNER";

    /** ACTIVE / LOCKED / INVITED */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled = false;

    /** ログイン失敗の連続回数によるロック（FR-A08）。しきい値到達で status=LOCKED ＋ lockedUntil を設定する。 */
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /** 無操作セッションタイムアウト判定用（FR-A09）。ログイン・リフレッシュ成功のたびに更新する。 */
    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;
}
