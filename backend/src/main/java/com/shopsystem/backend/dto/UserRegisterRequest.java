package com.shopsystem.backend.dto;

import lombok.Data;

/**
 * 現場スタッフの自己登録リクエスト（POST /api/v1/auth/register）。
 * テナント作成・オーナー登録は運営者がPostmanで行う前提（02_requirements.md §3.1／FR-A02c）。
 * 会社（テナント）はURLサブドメインで確定するため画面には持たせず、サーバ側で解決する。
 */
@Data
public class UserRegisterRequest {
    private String name;
    private String email;
    private String password;
    /** 任意入力。 */
    private String telnumber;
    /**
     * "HALL"（スタッフ）または "PARTTIME"（アルバイト）のみ許可。
     * 店長・オーナー等への昇格は、ログイン後のユーザー編集画面で行う。
     */
    private String role;
}
