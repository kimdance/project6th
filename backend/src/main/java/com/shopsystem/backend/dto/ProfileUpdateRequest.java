package com.shopsystem.backend.dto;

import lombok.Data;

/**
 * PUT /api/v1/auth/me のリクエストボディ。ログイン中の本人が、自分の氏名・メールアドレス
 * （ログインIDでもある）・電話番号を変更する。ロール・所属店舗はここでは変更できない
 * （経営管理者が「ユーザー管理」画面で行う。02_requirements.md §3.2）。
 */
@Data
public class ProfileUpdateRequest {
    private String name;
    private String email;
    /** 任意入力。 */
    private String telnumber;
}
