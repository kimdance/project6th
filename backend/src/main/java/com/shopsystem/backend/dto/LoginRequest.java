package com.shopsystem.backend.dto;

import lombok.Data;

/** POST /api/v1/auth/login のリクエストボディ。テナントはURLサブドメイン由来のセッションから解決する。 */
@Data
public class LoginRequest {
    private String email;
    private String password;
}
