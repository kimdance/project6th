package com.shopsystem.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TenantResolutionInterceptorTest {

    @Test
    void サブドメインのラベルを小文字で取り出す() {
        assertThat(TenantResolutionInterceptor.extractCompanyCode("Acme-Izakaya.localhost"))
                .isEqualTo("acme-izakaya");
    }

    @Test
    void ポート番号は無視する() {
        assertThat(TenantResolutionInterceptor.extractCompanyCode("acme-izakaya.localhost:8080"))
                .isEqualTo("acme-izakaya");
    }

    @Test
    void サブドメインなしはラベル全体を返す_存在しないため404扱いになる() {
        assertThat(TenantResolutionInterceptor.extractCompanyCode("localhost")).isEqualTo("localhost");
        assertThat(TenantResolutionInterceptor.extractCompanyCode("localhost:8080")).isEqualTo("localhost");
    }

    @Test
    void Hostヘッダがなければ空文字() {
        assertThat(TenantResolutionInterceptor.extractCompanyCode(null)).isEmpty();
        assertThat(TenantResolutionInterceptor.extractCompanyCode("")).isEmpty();
    }
}
