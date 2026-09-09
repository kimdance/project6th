package com.shopsystem.backend.dto;

import lombok.Data;

/**
 * 新規テナント登録（サインアップ）リクエスト。
 * accounts.&lt;サービスドメイン&gt; 経由で受け付ける（04_architecture.md §6.1）。
 */
@Data
public class SignupRequest {
    private String companyCode;
    private String companyName;
    private String ownerName;
    private String ownerEmail;
    private String password;
}
