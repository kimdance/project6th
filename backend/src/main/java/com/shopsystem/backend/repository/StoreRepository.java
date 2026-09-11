package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {

    // テナント外の store_id を指定されても存在しないものとして扱う（04_architecture.md §3.2）。
    Optional<Store> findByIdAndCompany_Id(Long id, Long companyId);

    // 1テナントが複数店舗を持てるため一覧で返す（04_architecture.md §2.5）。
    List<Store> findByCompany_IdOrderById(Long companyId);

    // ホーム画面のメニュー出し分け（店舗が1件も無ければ「店舗設定」以外を隠す）に使う。
    boolean existsByCompany_Id(Long companyId);
}
