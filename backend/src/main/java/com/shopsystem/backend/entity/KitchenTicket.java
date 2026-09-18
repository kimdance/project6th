package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * キッチン伝票。docs/04_architecture.md §4.6 の kitchen_ticket テーブルに対応（FR-E04）。
 * 注文送信時、調理が必要な明細（{@code menu_item.prep_type = COOK}）を1件でも含む場合に発行する。
 * フェーズ1では専用のKDS画面は未実装のため、status は発行時にNEWで作成し、対象注文の
 * 全明細がSERVED/CANCELLED/REJECTEDになった時点でDONEへ更新するのみ（IN_PROGRESSへの遷移は
 * KDS画面の実装時に対応）。offline_settled は04§9のオフライン同期向けの列で、このエンティティ
 * （オンライン専用）では常にfalse。
 */
@Entity
@Table(name = "kitchen_ticket")
@Data
@EqualsAndHashCode(callSuper = true)
public class KitchenTicket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Store store;

    /** NEW / IN_PROGRESS / DONE */
    @Column(nullable = false, length = 20)
    private String status = "NEW";

    @Column(name = "offline_settled", nullable = false)
    private boolean offlineSettled = false;

    @Column(name = "printed_at")
    private LocalDateTime printedAt;
}
