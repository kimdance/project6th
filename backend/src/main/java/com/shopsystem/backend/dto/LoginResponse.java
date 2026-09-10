package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** POST /api/v1/auth/login・POST /api/v1/auth/refresh のレスポンス。 */
@Data
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
}
