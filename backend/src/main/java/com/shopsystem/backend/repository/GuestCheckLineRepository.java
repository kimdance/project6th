package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.GuestCheckLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GuestCheckLineRepository extends JpaRepository<GuestCheckLine, Long> {

    List<GuestCheckLine> findAllByCheck_Id(Long checkId);

    boolean existsByOrderLine_Id(Long orderLineId);

    void deleteAllByCheck_Id(Long checkId);
}
