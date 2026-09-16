package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findAllByStore_IdOrderByDisplayOrderAscIdAsc(Long storeId);

    Optional<MenuItem> findByIdAndStore_Id(Long id, Long storeId);
}
