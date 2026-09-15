package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.AuditLog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * FR-J04：期間・実行者・操作種別で絞り込める検索（他テナントの行は決して返さない）。
     * 経営管理者（全店）向け。店長向けは {@link #searchRestrictedToStores} を使う。
     */
    // :from/:to は cast() で明示的に型付けする。PostgreSQL は "? is null" のような
    // 型を推論できない使い方の場合にパラメータの型を決められずエラーになるため（$8 could not
    // determine data type of parameter）。storeId/action/actor は他の比較から推論できるため不要。
    @Query("""
            select a from AuditLog a
            where a.companyCode = :companyCode
              and (:storeId is null or a.store.id = :storeId)
              and (:action is null or a.action = :action)
              and (:actor is null or a.actor = :actor)
              and (cast(:from as timestamp) is null or a.occurredAt >= :from)
              and (cast(:to as timestamp) is null or a.occurredAt < :to)
            order by a.occurredAt desc
            """)
    Page<AuditLog> search(
            @Param("companyCode") String companyCode,
            @Param("storeId") Long storeId,
            @Param("action") String action,
            @Param("actor") String actor,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /**
     * 店長向け：自分の所属店舗（{@code allowedStoreIds}）に紐づく操作のみを返す。
     * store_id が null の全社共通操作（ログイン・パスワード変更・ユーザー権限変更等）は
     * 対象外になる（店舗に紐づかないため）。{@code allowedStoreIds} が空の場合は呼び出し側で
     * クエリを発行せず空ページを返すこと（空集合を渡すと "in ()" が不正なSQLになるため）。
     */
    @Query("""
            select a from AuditLog a
            where a.companyCode = :companyCode
              and a.store.id in :allowedStoreIds
              and (:storeId is null or a.store.id = :storeId)
              and (:action is null or a.action = :action)
              and (:actor is null or a.actor = :actor)
              and (cast(:from as timestamp) is null or a.occurredAt >= :from)
              and (cast(:to as timestamp) is null or a.occurredAt < :to)
            order by a.occurredAt desc
            """)
    Page<AuditLog> searchRestrictedToStores(
            @Param("companyCode") String companyCode,
            @Param("allowedStoreIds") Collection<Long> allowedStoreIds,
            @Param("storeId") Long storeId,
            @Param("action") String action,
            @Param("actor") String actor,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
