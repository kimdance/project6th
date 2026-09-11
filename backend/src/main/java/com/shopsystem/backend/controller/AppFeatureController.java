package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.AppFeatureResponse;
import com.shopsystem.backend.service.AppFeatureService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ログイン後の共通トップ画面（ホーム）に並べる機能の入口の一覧。
 * ログイン済み前提（{@code JwtAuthenticationInterceptor} が事前に認証・テナント文脈を確立する）で、
 * 呼び出し元のロールに表示可能なものだけを返す。
 */
@RestController
@RequestMapping("/api/v1/app-features")
@RequiredArgsConstructor
public class AppFeatureController {

    private final AppFeatureService appFeatureService;

    @GetMapping
    public List<AppFeatureResponse> list() {
        return appFeatureService.listForCurrentUser();
    }
}
