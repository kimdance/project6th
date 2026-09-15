package com.shopsystem.backend.dto;

import lombok.Data;

/** POST /api/v1/auth/refresh のリクエストボディ。 */
@Data
public class RefreshRequest {
    private String refreshToken;
}
