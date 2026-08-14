package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // 会社コードとメールアドレスの組み合わせの存在チェック用メソッド（Spring Data JPAが自動生成）
    boolean existsByCompanyCodeAndEmail(String companyCode, String email);
}