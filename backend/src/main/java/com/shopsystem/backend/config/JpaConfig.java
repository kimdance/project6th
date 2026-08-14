package com.shopsystem.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

@Configuration
public class JpaConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        // ログイン機能実装前や匿名登録時は "SYSTEM" などを返します
        // 将来 Spring Security 等を導入したら、ログインユーザー名を返す処理に変更できます
        return () -> Optional.of("SYSTEM");
    }
}