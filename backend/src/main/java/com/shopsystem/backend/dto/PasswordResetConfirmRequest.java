package com.shopsystem.backend.dto;

import lombok.Data;

/** POST /api/v1/auth/password-reset/confirm のリクエストボディ（FR-A04）。 */
@Data
public class PasswordResetConfirmRequest {
    /** メールのリンクに含まれる、生のトークン文字列（ハッシュ化前）。 */
    private String token;
    private String password;
}
