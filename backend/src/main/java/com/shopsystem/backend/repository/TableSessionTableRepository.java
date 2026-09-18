package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.TableSessionTable;
import com.shopsystem.backend.entity.TableSessionTableId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableSessionTableRepository extends JpaRepository<TableSessionTable, TableSessionTableId> {

    List<TableSessionTable> findAllByTableSession_Id(Long tableSessionId);

    Optional<TableSessionTable> findFirstByTableSession_IdAndPrimaryTrue(Long tableSessionId);
}
