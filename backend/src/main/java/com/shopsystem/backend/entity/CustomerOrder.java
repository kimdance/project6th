package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 注文（1回の送信）。docs/04_architecture.md §4.6 の customer_order テーブルに対応
 * （FR-E02。03_domain_model.md §4.4）。モバイルオーダー（source=MOBILE。FR-F）は別途対応する
 * ため、フェーズ1のこのエンティティは source=STAFF のみを扱う（自動 ACCEPTED。§3.2「注文の
 * 入力…はホールまで可」）。client_ref_id・mobile_order_session_id はオフライン同期・モバイル
 * オーダー実装時に使う列で、このエンティティでは常にnull。
 */
@Entity
@Table(name = "customer_order")
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_session_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TableSession tableSession;

    /** STAFF / MOBILE（フェーズ1はSTAFFのみ） */
    @Column(nullable = false, length = 20)
    private String source = "STAFF";

    @Column(name = "entered_by", length = 255)
    private String enteredBy;

    @Column(name = "mobile_order_session_id")
    private Long mobileOrderSessionId;

    @Column(name = "client_ref_id", length = 40)
    private String clientRefId;

    /** SUBMITTED / ACCEPTED / REJECTED（STAFF注文は登録時に自動ACCEPTED） */
    @Column(nullable = false, length = 20)
    private String status = "ACCEPTED";

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "accepted_by", length = 255)
    private String acceptedBy;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;
}
