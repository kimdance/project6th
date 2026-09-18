package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.TableSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableSessionRepository extends JpaRepository<TableSession, Long> {

    Optional<TableSession> findByIdAndStore_Id(Long id, Long storeId);

    List<TableSession> findAllByStore_IdAndStatusInOrderByOpenedAt(Long storeId, List<String> statuses);
}
