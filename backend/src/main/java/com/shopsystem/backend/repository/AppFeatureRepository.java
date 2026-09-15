package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.AppFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppFeatureRepository extends JpaRepository<AppFeature, Long> {

    // ログイン中ユーザーのロールで表示可否を判定する（DBの app_feature_role を参照）。
    @Query("select f from AppFeature f join f.roles r "
            + "where f.active = true and r = :role order by f.displayOrder asc")
    List<AppFeature> findVisibleForRole(@Param("role") String role);
}
