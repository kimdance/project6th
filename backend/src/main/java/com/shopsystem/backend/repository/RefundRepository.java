package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findAllByCheck_IdOrderByExecutedAt(Long checkId);
}
