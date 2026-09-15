package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * パスワード再設定トークン（FR-A04・EXT-03）。docs/04_architecture.md の
 * password_reset_token に対応。派生・キュー的なテーブルのため created_by／updated_by は持たない
 * （V1 冒頭コメントの分類方針）。
 *
 * {@code tokenHash} にはメール本文に載せる生トークンそのものではなく、SHA-256ハッシュを保存する
 * （DB漏えい時に生トークンとして使い回されないようにする。PasswordResetService）。
 */
@Entity
@Table(name = "password_reset_token")
@Data
@EqualsAndHashCode
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** null = 未使用。使用済み・失効済みになった時刻。 */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
