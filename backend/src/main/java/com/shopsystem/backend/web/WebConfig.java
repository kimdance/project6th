package com.shopsystem.backend.web;

import com.shopsystem.backend.service.FileStorageService;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.Filter;

import java.nio.file.Path;
import java.util.List;

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

    /**
     * CORSは {@code WebMvcConfigurer#addCorsMappings}（HandlerMapping経由）ではなく、
     * サーブレット {@link Filter} として登録する。{@code addCorsMappings} 方式は
     * {@code DispatcherServlet#getHandler} の内部で適用されるため、それより前段の
     * {@code DispatcherServlet#checkMultipart}（マルチパート解析）で例外
     * （{@link org.springframework.web.multipart.MaxUploadSizeExceededException} 等）が
     * 発生した場合はCORSヘッダが一切付与されない。その結果、バックエンドは
     * 「写真ファイルが大きすぎます」等の正しい400を返しているにもかかわらず、
     * ブラウザ側はCORS違反として応答をJSへ渡さず、フロントには汎用の通信エラー
     * （実際のエラーメッセージが握りつぶされる）として見えてしまう不具合があった
     * （メニュー写真アップロード、FR-D01。2026-09-17）。Filterはリクエスト処理全体を
     * 包むため、例外の発生段階によらず一貫してCORSヘッダを付与できる。
     * 開発用：フロント（Vite, 5173番ポート）とバックエンド（8080番ポート）はサブドメインが
     * 同じでもポートが異なるため別オリジン扱いになる。認証は Bearer トークンでCookieを
     * 使わないため allowCredentials は不要。本番のオリジン許可方針は別途検討する。
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://*.localhost:5173", "http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
