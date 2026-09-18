package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.GuestCheckDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GuestCheckDiscountRepository extends JpaRepository<GuestCheckDiscount, Long> {

    List<GuestCheckDiscount> findAllByCheck_Id(Long checkId);
}
