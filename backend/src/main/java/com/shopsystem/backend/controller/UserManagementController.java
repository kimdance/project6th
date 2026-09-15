package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.UserSummaryResponse;
import com.shopsystem.backend.dto.UserUpdateRequest;
import com.shopsystem.backend.service.UserManagementService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ユーザー管理（一覧・役割／所属店舗の変更）。経営管理者のみ（02_requirements.md §3.2）。
 * ログイン済み前提（{@code JwtAuthenticationInterceptor} が事前に認証・テナント文脈を確立する）。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserManagementService userManagementService;

    @GetMapping
    public List<UserSummaryResponse> list() {
        return userManagementService.list();
    }

    @PutMapping("/{userId}")
    public UserSummaryResponse update(@PathVariable Long userId, @RequestBody UserUpdateRequest body) {
        return userManagementService.update(userId, body);
    }
}
