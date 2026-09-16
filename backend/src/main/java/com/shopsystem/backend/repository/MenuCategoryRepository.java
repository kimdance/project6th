package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.MenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {

    List<MenuCategory> findAllByStore_IdOrderByDisplayOrderAscIdAsc(Long storeId);

    Optional<MenuCategory> findByIdAndStore_Id(Long id, Long storeId);
}
