package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    List<CustomerOrder> findAllByTableSession_IdOrderBySubmittedAt(Long tableSessionId);
}
