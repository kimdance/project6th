package com.shopsystem.backend.dto;

import lombok.Data;

/**
 * POST /api/v1/auth/password-reset のリクエストボディ（FR-A04）。
 * 会社（テナント）はURLサブドメインで確定するため画面には持たせない。
 */
@Data
public class PasswordResetRequest {
    private String email;
}
