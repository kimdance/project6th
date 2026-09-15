package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.AppFeatureResponse;
import com.shopsystem.backend.repository.AppFeatureRepository;
import com.shopsystem.backend.repository.StoreRepository;
import com.shopsystem.backend.web.TenantContext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ログイン後の共通トップ画面（ホーム）に並べる機能の入口。表示可否はDB（app_feature_role）で
 * 管理し、ログイン中ユーザーのロールに合うものだけを返す（旧・フロント直書きの features.ts を置換）。
 * さらに、自テナントに店舗が1件も無い間は {@code requiresStore = true} の機能（卓・決済手段・
 * 営業日など）を隠す。テナント登録直後（経営管理者は作成済みだが店舗は未作成）に、押しても
 * 先に進めないカードを見せないため。
 */
@Service
@RequiredArgsConstructor
public class AppFeatureService {

    private final AppFeatureRepository appFeatureRepository;
    private final StoreRepository storeRepository;

    @Transactional(readOnly = true)
    public List<AppFeatureResponse> listForCurrentUser() {
        TenantContext.Data ctx = TenantContext.get();
        boolean hasStore = storeRepository.existsByCompany_Id(ctx.companyId());

        return appFeatureRepository.findVisibleForRole(ctx.role()).stream()
                .filter(f -> hasStore || !f.isRequiresStore())
                .map(f -> new AppFeatureResponse(f.getFeatureKey(), f.getTitle(), f.getDescription(), f.getPath()))
                .toList();
    }
}
