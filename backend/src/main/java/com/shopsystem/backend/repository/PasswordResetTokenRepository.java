package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // 新しいリセットを発行する際、古い未使用トークンを失効させるために使う。
    List<PasswordResetToken> findByUser_IdAndUsedAtIsNull(Long userId);
}
