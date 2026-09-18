package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.KitchenTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KitchenTicketRepository extends JpaRepository<KitchenTicket, Long> {

    Optional<KitchenTicket> findFirstByOrder_Id(Long orderId);
}
