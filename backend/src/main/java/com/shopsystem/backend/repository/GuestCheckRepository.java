package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.GuestCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GuestCheckRepository extends JpaRepository<GuestCheck, Long> {

    List<GuestCheck> findAllByTableSession_IdOrderBySeqInSession(Long tableSessionId);

    Optional<GuestCheck> findByIdAndStore_Id(Long id, Long storeId);

    int countByTableSession_Id(Long tableSessionId);

    long countByTableSession_IdAndStatusIn(Long tableSessionId, List<String> statuses);
}
