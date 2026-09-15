package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 予約通知の送信記録。docs/04_architecture.md §4.5 の reservation_notification に対応（FR-C05）。
 * createdAt/updatedAtのみ持ち、createdBy/updatedByは無い（BaseEntity不使用。物理スキーマに合わせる）。
 */
@Entity
@Table(name = "reservation_notification")
@Data
@EqualsAndHashCode
public class ReservationNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Reservation reservation;

    /** CONFIRM（受付確認）／REMINDER（前日リマインド。フェーズ1未実装） */
    @Column(nullable = false, length = 20)
    private String type;

    /** EMAIL／SMS */
    @Column(nullable = false, length = 20)
    private String channel;

    // "to" はPostgreSQLの予約語のため、物理名をバッククォートで囲みHibernateに引用符付与を指示する
    // （V1__init_schema.sqlでも "to" と引用符付きで定義済み）。
    @Column(name = "`to`", nullable = false, length = 255)
    private String to;

    /** QUEUED／SENT／FAILED */
    @Column(nullable = false, length = 20)
    private String status = "QUEUED";

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
