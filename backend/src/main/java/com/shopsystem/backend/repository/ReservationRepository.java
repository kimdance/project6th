package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findAllByStore_IdAndReservedAtGreaterThanEqualAndReservedAtLessThanOrderByReservedAt(
            Long storeId, LocalDateTime from, LocalDateTime to);

    // 経営管理者向け：自テナント全店舗を横断した一覧（FR-C02。店舗フィルタ未指定時）。
    List<Reservation> findAllByStore_Company_IdAndReservedAtGreaterThanEqualAndReservedAtLessThanOrderByReservedAt(
            Long companyId, LocalDateTime from, LocalDateTime to);

    // 店長・ホール向け：自分が所属する複数店舗を横断した一覧（店舗フィルタ未指定時）。
    List<Reservation> findAllByStore_IdInAndReservedAtGreaterThanEqualAndReservedAtLessThanOrderByReservedAt(
            Set<Long> storeIds, LocalDateTime from, LocalDateTime to);

    Optional<Reservation> findByIdAndStore_Id(Long id, Long storeId);
}
