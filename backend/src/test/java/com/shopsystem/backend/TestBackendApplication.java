package com.shopsystem.backend;

import org.springframework.boot.SpringApplication;

/**
 * ローカルで使い捨て PostgreSQL コンテナに対してアプリを起動するためのランチャ。
 * 実行: ./mvnw spring-boot:test-run
 * （開発DB ShopSystemDB には接続しない。Flyway でスキーマが作られる）
 */
public class TestBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(BackendApplication::main)
				.with(TestcontainersConfiguration.class)
				.run(args);
	}
}
