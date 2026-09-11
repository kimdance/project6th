package com.shopsystem.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** POST /api/v1/auth/register のレスポンス。 */
@Data
@AllArgsConstructor
public class UserRegisterResponse {
    private Long userId;
    private String name;
    private String email;
    private String role;
}
