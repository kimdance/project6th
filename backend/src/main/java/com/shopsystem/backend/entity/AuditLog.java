package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 監査ログ（FR-J01〜FR-J05。04_architecture.md §4.10）。テーブル自体は V1__init_schema.sql で
 * 作成済み（occurred_at の月次レンジパーティション。V1では最低限の稼働のため DEFAULT パーティション
 * を1つ用意）。DB上の主キーはパーティションキーを含む複合 (id, occurred_at) だが、id は
 * IDENTITY 採番で単独でも一意なため、JPA上は id のみを @Id とする。
 *
 * アプリからの更新・削除は行わない（FR-J03）ため {@link BaseEntity} は継承しない
 * （更新者・更新日時の概念を持たせないため）。company は非正規化コピーの
 * {@link #companyCode} のみを持ち、FKは張らない（§3.1・§4.10）。actor はユーザーの
 * 特定情報（メールアドレス等）、または未特定・システム操作は "SYSTEM"／"unknown:..." を持つ
 * 表示用の文字列で、FR-J04「実行者で絞り込み」もこの列への一致で行う。
 */
@Entity
@Table(name = "audit_log")
@Data
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_code", nullable = false, length = 63)
    private String companyCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Store store;

    @Column(nullable = false, length = 255)
    private String actor;

    /** 操作種別（{@link com.shopsystem.backend.service.AuditActions}）。 */
    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "before_summary", columnDefinition = "text")
    private String beforeSummary;

    @Column(name = "after_summary", columnDefinition = "text")
    private String afterSummary;

    @Column(length = 45)
    private String ip;

    @Column(length = 255)
    private String device;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();
}
