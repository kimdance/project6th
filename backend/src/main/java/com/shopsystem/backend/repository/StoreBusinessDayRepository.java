package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.StoreBusinessDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StoreBusinessDayRepository extends JpaRepository<StoreBusinessDay, Long> {

    List<StoreBusinessDay> findAllByStore_IdAndWeekdayIsNotNull(Long storeId);

    List<StoreBusinessDay> findAllByStore_IdAndBusinessDateIsNotNullOrderByBusinessDate(Long storeId);

    Optional<StoreBusinessDay> findByStore_IdAndWeekday(Long storeId, Short weekday);

    Optional<StoreBusinessDay> findByStore_IdAndBusinessDate(Long storeId, LocalDate businessDate);

    Optional<StoreBusinessDay> findByIdAndStore_Id(Long id, Long storeId);

    boolean existsByStore_IdAndBusinessDate(Long storeId, LocalDate businessDate);
}
