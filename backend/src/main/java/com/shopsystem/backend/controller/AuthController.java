package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.LoginRequest;
import com.shopsystem.backend.dto.LoginResponse;
import com.shopsystem.backend.dto.RefreshRequest;
import com.shopsystem.backend.dto.TenantInfoResponse;
import com.shopsystem.backend.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping("/tenant")
    public TenantInfoResponse tenant(HttpServletRequest request) {
        return authService.currentTenant(request);
    }

    @PostMapping("/login")
    public LoginResponse login(HttpServletRequest request, @RequestBody LoginRequest body) {
        return authService.login(request, body);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(HttpServletRequest request, @RequestBody RefreshRequest body) {
        return authService.refresh(request, body.getRefreshToken());
    }
}
