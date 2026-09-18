package com.shopsystem.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 卓セッション⇔卓（table_session_table）。docs/04_architecture.md §4.6 に対応。
 * 卓結合（FR-E06）はフェーズ1未対応のため、1セッションにつき常に1件（is_primary=true）。
 */
@Entity
@Table(name = "table_session_table")
@Data
public class TableSessionTable {

    @EmbeddedId
    private TableSessionTableId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("tableSessionId")
    @JoinColumn(name = "table_session_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TableSession tableSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("diningTableId")
    @JoinColumn(name = "dining_table_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private DiningTable diningTable;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
