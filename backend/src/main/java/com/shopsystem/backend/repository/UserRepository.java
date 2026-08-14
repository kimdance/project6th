package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // メールアドレスの存在チェック用メソッド（Spring Data JPAが自動生成）
    boolean existsByEmail(String email);
}