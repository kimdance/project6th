package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {

    // テナント外の store_id を指定されても存在しないものとして扱う（04_architecture.md §3.2）。
    Optional<Store> findByIdAndCompany_Id(Long id, Long companyId);
}
