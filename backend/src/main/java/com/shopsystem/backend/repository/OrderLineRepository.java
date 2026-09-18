package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.OrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    List<OrderLine> findAllByTableSession_IdOrderByRegisteredAt(Long tableSessionId);

    List<OrderLine> findAllByOrder_Id(Long orderId);

    Optional<OrderLine> findByIdAndTableSession_Store_Id(Long id, Long storeId);
}
