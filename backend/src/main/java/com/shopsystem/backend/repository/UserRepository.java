package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // ログイン照合・重複チェックはテナント（company_id）＋ email で行う（04_architecture.md §3.1 / §6.1）。
    // company_id はサブドメイン由来のセッションから解決した値を渡す。
    Optional<User> findByCompany_IdAndEmail(Long companyId, String email);

    boolean existsByCompany_IdAndEmail(Long companyId, String email);

    // ユーザー管理画面（GET /api/v1/users）用。テナント外の user_id を指定されても存在しないものとして扱う。
    List<User> findByCompany_IdOrderById(Long companyId);

    Optional<User> findByIdAndCompany_Id(Long id, Long companyId);

    // 経営管理者を0人にする変更を防ぐためのカウント（UserManagementService）。
    long countByCompany_IdAndRole(Long companyId, String role);
}
