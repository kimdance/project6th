package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * ユーザー招待。docs/04_architecture.md §4.3 の user_invitation テーブルに対応。
 * 既存テナントへの参加経路（会社は既に存在するため招待受諾はその会社のサブドメインで行う。§6.1）。
 */
@Entity
@Table(name = "user_invitation")
@Data
@EqualsAndHashCode(callSuper = true)
public class UserInvitation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Store store;

    @Column(nullable = false)
    private String email;

    /** OWNER / MANAGER / HALL / KITCHEN / PARTTIME */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
}
