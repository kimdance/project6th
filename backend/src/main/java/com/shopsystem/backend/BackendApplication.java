package com.shopsystem.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing // ★JPA Auditingを有効化
public class BackendApplication {

    public static void main(String[] args) {
        // JVMのネットワークスタックがIPv6を優先してしまうと、application.properties の
        // server.address=0.0.0.0 を指定してもTomcatが実際にはIPv6ソケット（[::]:8080）を
        // 開いてしまう。WSL2のWindows→WSL2ポートフォワーディングはIPv4ソケットを基準に
        // 転送対象を判定するため、その場合Windows側のブラウザから localhost:8080 に一切
        // 到達できなくなる（開発機がWSL2の場合の不具合。docs/ops/dev-machine-setup.md）。
        // Tomcat初期化より前に設定する必要があるため main() の先頭で行う。
        System.setProperty("java.net.preferIPv4Stack", "true");
        SpringApplication.run(BackendApplication.class, args);
    }
}