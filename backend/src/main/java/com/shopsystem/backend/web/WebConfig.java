package com.shopsystem.backend.web;

import com.shopsystem.backend.service.FileStorageService;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final TenantResolutionInterceptor tenantResolutionInterceptor;
    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // /api/v1/admin/tenants はテナントがまだ存在しないため対象外（04_architecture.md §6.2）。
        // /api/v1/auth/me はログイン後の画面用でテナントはJWTから解決するため、Host由来の解決対象から除外する。
        // /api/v1/public/** はお客様向けWeb予約（認証不要。FR-C03〜C06。§6.1 2026-09-15追補）。
        registry.addInterceptor(tenantResolutionInterceptor)
                .addPathPatterns("/api/v1/auth/**", "/api/v1/public/**")
                .excludePathPatterns("/api/v1/auth/me");
        // ログイン済み前提のAPI（04_architecture.md §3.2）。/api/v1/auth/me もアクセストークン必須。
        registry.addInterceptor(jwtAuthenticationInterceptor)
                .addPathPatterns(
                        "/api/v1/stores/**", "/api/v1/auth/me", "/api/v1/app-features", "/api/v1/users/**",
                        "/api/v1/audit-logs/**", "/api/v1/reservations/**");
    }

    /**
     * アップロード済みのメニュー写真（FR-D01）を認証不要で配信する（注文画面等でも表示するため。
     * {@link FileStorageService} が保存するローカルディスクのパスをそのまま公開する。フェーズ1の
     * 暫定ストレージであり、本番のオブジェクトストレージ選定後は配信方式ごと差し替える。§6.5参照）。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Path.of(uploadDir).toAbsolutePath() + "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 開発用：フロント（Vite, 5173番ポート）とバックエンド（8080番ポート）がサブドメインは
        // 同じでもポートが異なるため別オリジン扱いになる。認証は Bearer トークンでCookieを
        // 使わないため allowCredentials は不要。本番のオリジン許可方針は別途検討する。
        registry.addMapping("/api/v1/**")
                .allowedOriginPatterns("http://*.localhost:5173", "http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders("*");
    }
}
