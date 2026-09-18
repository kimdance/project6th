package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.GuestCheckTaxLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GuestCheckTaxLineRepository extends JpaRepository<GuestCheckTaxLine, Long> {

    List<GuestCheckTaxLine> findAllByCheck_Id(Long checkId);

    void deleteAllByCheck_Id(Long checkId);
}
