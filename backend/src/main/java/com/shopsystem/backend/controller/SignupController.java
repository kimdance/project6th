package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.SignupRequest;
import com.shopsystem.backend.entity.Company;
import com.shopsystem.backend.service.SignupService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 新規テナント登録（サインアップ）。
 * 本番では accounts.&lt;サービスドメイン&gt; 経由で受け付ける（04_architecture.md §6.1）。
 * ホスト（accounts.）の強制は、サブドメイン解決フィルタの実装時に追加する。
 */
@RestController
@RequestMapping("/api/v1/signup")
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> signup(@RequestBody SignupRequest request) {
        Company company = signupService.signup(request);
        return Map.of("companyCode", company.getCompanyCode());
    }
}
