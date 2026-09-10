package com.shopsystem.backend.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
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
        registry.addInterceptor(tenantResolutionInterceptor).addPathPatterns("/api/v1/auth/**");
        // ログイン済み前提の業務API（04_architecture.md §3.2）。
        registry.addInterceptor(jwtAuthenticationInterceptor).addPathPatterns("/api/v1/stores/**");
    }
}
