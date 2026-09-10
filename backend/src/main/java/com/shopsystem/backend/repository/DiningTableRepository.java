package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.DiningTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {

    List<DiningTable> findAllByStore_IdOrderByTableNo(Long storeId);

    Optional<DiningTable> findByIdAndStore_Id(Long id, Long storeId);

    boolean existsByStore_IdAndTableNo(Long storeId, String tableNo);

    boolean existsByStore_IdAndQrToken(Long storeId, String qrToken);
}
