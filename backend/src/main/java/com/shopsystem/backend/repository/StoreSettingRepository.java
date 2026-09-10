package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.StoreSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreSettingRepository extends JpaRepository<StoreSetting, Long> {
    // 主キーは store_id そのもの（store と1:1）。findById(storeId) でそのまま引ける。
}
