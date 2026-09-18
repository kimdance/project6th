package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 卓セッション（来店）。docs/04_architecture.md §4.6 の table_session テーブルに対応
 * （FR-E01。03_domain_model.md §4.2の状態遷移）。courseId はコース機能（FR-B06）が
 * フェーズ1未実装のため常にnull（実体参照は持たずIDのみ保持。Reservation.courseIdと同じ扱い）。
 * 卓結合・分割（FR-E06。S区分）はフェーズ1未対応のため、1セッションにつき卓は常に1件
 * （{@link #tables}）。
 */
@Entity
@Table(name = "table_session")
@Data
@EqualsAndHashCode(callSuper = true)
public class TableSession extends BaseEntity {

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

    /** OPEN / BILLING / CLOSED（03_domain_model.md §4.2）。BILLING／CLOSEDはFR-G実装時に対応。 */
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "opened_by", nullable = false, length = 255)
    private String openedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "course_started_at")
    private LocalDateTime courseStartedAt;

    @Column(name = "last_order_at")
    private LocalDateTime lastOrderAt;
}
