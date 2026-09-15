package com.shopsystem.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    /**
     * パスワードは {bcrypt} プレフィックス付きで保存する（DelegatingPasswordEncoder）。
     * 将来アルゴリズムを移行しても既存ハッシュを検証できる（NFR-08 / FR-A01）。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
