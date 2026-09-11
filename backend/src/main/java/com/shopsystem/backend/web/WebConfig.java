package com.shopsystem.backend.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final TenantResolutionInterceptor tenantResolutionInterceptor;
    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // /api/v1/admin/tenants はテナントがまだ存在しないため対象外（04_architecture.md §6.2）。
        // /api/v1/auth/me はログイン後の画面用でテナントはJWTから解決するため、Host由来の解決対象から除外する。
        registry.addInterceptor(tenantResolutionInterceptor)
                .addPathPatterns("/api/v1/auth/**")
                .excludePathPatterns("/api/v1/auth/me");
        // ログイン済み前提のAPI（04_architecture.md §3.2）。/api/v1/auth/me もアクセストークン必須。
        registry.addInterceptor(jwtAuthenticationInterceptor)
                .addPathPatterns(
                        "/api/v1/stores/**", "/api/v1/auth/me", "/api/v1/app-features", "/api/v1/users/**");
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
