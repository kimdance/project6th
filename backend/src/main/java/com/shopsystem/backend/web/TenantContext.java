package com.shopsystem.backend.web;

/**
 * 認証済みリクエストのテナント文脈（04_architecture.md §3.2）。
 * {@link JwtAuthenticationInterceptor} がアクセストークンのクレームから設定し、
 * リクエスト終了時に必ずクリアする（スレッドプール再利用時の漏えい防止）。
 */
public final class TenantContext {

    /** storeId は null の場合あり（全店＝本部ユーザー）。 */
    public record Data(Long companyId, String companyCode, Long userId, String role, Long storeId) {
    }

    private static final ThreadLocal<Data> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Data data) {
        HOLDER.set(data);
    }

    public static Data get() {
        Data data = HOLDER.get();
        if (data == null) {
            throw new IllegalStateException(
                    "TenantContext is not set. JwtAuthenticationInterceptor must run before this call.");
        }
        return data;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
