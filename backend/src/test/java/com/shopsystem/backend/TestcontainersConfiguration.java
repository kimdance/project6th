package com.shopsystem.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * テスト用の PostgreSQL を Testcontainers で起動する。
 *
 * {@code @ServiceConnection} により spring.datasource.* がコンテナへ自動的に上書きされるため、
 * application.properties の接続先（開発DB）や環境変数は不要。Flyway はこのコンテナに対して
 * V1__init_schema.sql を適用し、Hibernate の ddl-auto=validate がその結果を検証する。
 *
 * イメージは開発・本番の PostgreSQL メジャー（16 系）に合わせる。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }
}
