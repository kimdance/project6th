package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findAllByCheck_IdOrderByProcessedAt(Long checkId);
}
