package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 予約。docs/04_architecture.md §4.5 の reservation テーブルに対応（FR-C01〜C09）。
 * companyCode は非正規化コピー（FKは張らない。§3.1）。courseId は03_domain_model.mdのFK先だが、
 * コース機能（FR-B06）はフェーズ1未実装のため常にnull（実体参照は持たずIDのみ保持）。
 */
@Entity
@Table(name = "reservation")
@Data
@EqualsAndHashCode(callSuper = true)
public class Reservation extends BaseEntity {

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

    @Column(name = "reserved_at", nullable = false)
    private LocalDateTime reservedAt;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Column(name = "guest_name", nullable = false, length = 100)
    private String guestName;

    @Column(name = "guest_phone", length = 30)
    private String guestPhone;

    @Column(name = "guest_email", length = 255)
    private String guestEmail;

    @Column(name = "request_note", columnDefinition = "text")
    private String requestNote;

    @Column(name = "course_id")
    private Long courseId;

    /** WEB / PHONE / WALK_IN */
    @Column(nullable = false, length = 20)
    private String channel;

    /** REQUESTED / CONFIRMED / SEATED / DONE / CANCELLED / NO_SHOW（03_domain_model.md §4.1） */
    @Column(nullable = false, length = 20)
    private String status = "REQUESTED";

    @Column(name = "confirmed_by", length = 255)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_reason", length = 255)
    private String cancelledReason;
}
