package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.LoginRequest;
import com.shopsystem.backend.dto.LoginResponse;
import com.shopsystem.backend.dto.MeResponse;
import com.shopsystem.backend.dto.PasswordResetConfirmRequest;
import com.shopsystem.backend.dto.PasswordResetRequest;
import com.shopsystem.backend.dto.ProfileUpdateRequest;
import com.shopsystem.backend.dto.RefreshRequest;
import com.shopsystem.backend.dto.TenantInfoResponse;
import com.shopsystem.backend.dto.UserRegisterRequest;
import com.shopsystem.backend.dto.UserRegisterResponse;
import com.shopsystem.backend.service.AuthService;
import com.shopsystem.backend.service.PasswordResetService;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * サブドメインからのテナント解決とログイン（04_architecture.md §6.1／§6.3）。
 * テナント解決は {@code TenantResolutionInterceptor} が事前に行い、見つからなければ404を返す。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @GetMapping("/tenant")
    public TenantInfoResponse tenant(HttpServletRequest request) {
        return authService.currentTenant(request);
    }

    /**
     * ログイン中のユーザー情報（ログイン後の共通トップ画面の表示用）。
     * {@code Authorization: Bearer <アクセストークン>} が必須（{@code JwtAuthenticationInterceptor}）。
     */
    @GetMapping("/me")
    public MeResponse me() {
        return authService.currentUser();
    }

    /**
     * ログイン中の本人が、自分の氏名・メールアドレス・電話番号を変更する。
     * ロール・所属店舗はここでは変更できない（経営管理者が「ユーザー管理」画面で行う）。
     */
    @PutMapping("/me")
    public MeResponse updateMe(@RequestBody ProfileUpdateRequest body) {
        return authService.updateCurrentUser(body);
    }

    /**
     * 現場スタッフの自己登録。テナント作成・オーナー登録は運営者がPostmanで行う前提（FR-A02c）。
     * 会社コードはURLサブドメインから解決するためリクエストボディには含めない。
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserRegisterResponse register(HttpServletRequest request, @RequestBody UserRegisterRequest body) {
        return authService.register(request, body);
    }

    @PostMapping("/login")
    public LoginResponse login(HttpServletRequest request, @RequestBody LoginRequest body) {
        return authService.login(request, body);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(HttpServletRequest request, @RequestBody RefreshRequest body) {
        return authService.refresh(request, body.getRefreshToken());
    }

    /**
     * パスワードを忘れた場合のリセットメール送信を申し込む（FR-A04）。ボディは {@code { email }} のみ。
     * メールアドレスが見つからない場合も含め、常に同じ結果を返す（登録有無を漏らさないため）。
     */
    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(HttpServletRequest request, @RequestBody PasswordResetRequest body) {
        passwordResetService.requestReset(request, body);
    }

    /** リンクのトークンと新しいパスワードから、実際にパスワードを再設定する（FR-A04）。 */
    @PostMapping("/password-reset/confirm")
    public void confirmPasswordReset(@RequestBody PasswordResetConfirmRequest body) {
        passwordResetService.confirmReset(body);
    }
}
