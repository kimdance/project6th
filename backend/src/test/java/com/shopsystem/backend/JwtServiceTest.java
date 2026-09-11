package com.shopsystem.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopsystem.backend.config.JwtProperties;
import com.shopsystem.backend.entity.Company;
import com.shopsystem.backend.entity.Store;
import com.shopsystem.backend.entity.User;
import com.shopsystem.backend.security.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

class JwtServiceTest {

    private JwtProperties properties() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-at-least-32-bytes-long-for-hs256");
        props.setAccessTokenMinutes(15);
        props.setRefreshTokenDays(14);
        return props;
    }

    private User userWithStore() {
        Company company = new Company();
        company.setId(1L);
        company.setCompanyCode("acme-izakaya");

        Store store = new Store();
        store.setId(10L);

        User user = new User();
        user.setId(100L);
        user.setCompany(company);
        user.setStores(Set.of(store));
        user.setRole("OWNER");
        return user;
    }

    @Test
    void アクセストークンにクレームを積める() {
        JwtService jwtService = new JwtService(properties());
        User user = userWithStore();

        String token = jwtService.issueAccessToken(user);
        Claims claims = jwtService.parseAccessToken(token);

        assertThat(claims.getSubject()).isEqualTo("100");
        assertThat(claims.get("companyId", Long.class)).isEqualTo(1L);
        assertThat(claims.get("companyCode", String.class)).isEqualTo("acme-izakaya");
        assertThat(claims.get("storeIds", List.class)).containsExactly(10);
        assertThat(claims.get("role", String.class)).isEqualTo("OWNER");
    }

    @Test
    void 店舗未所属なら空のstoreIdsクレームになる() {
        JwtService jwtService = new JwtService(properties());
        User user = userWithStore();
        user.setStores(Set.of());

        Claims claims = jwtService.parseAccessToken(jwtService.issueAccessToken(user));

        assertThat(claims.get("storeIds", List.class)).isEmpty();
    }

    @Test
    void アクセストークンをリフレッシュトークンとして検証すると失敗する() {
        JwtService jwtService = new JwtService(properties());
        String accessToken = jwtService.issueAccessToken(userWithStore());

        assertThatThrownBy(() -> jwtService.parseRefreshToken(accessToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void リフレッシュトークンをアクセストークンとして検証すると失敗する() {
        JwtService jwtService = new JwtService(properties());
        String refreshToken = jwtService.issueRefreshToken(userWithStore());

        assertThatThrownBy(() -> jwtService.parseAccessToken(refreshToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 異なる署名鍵で発行したトークンは検証に失敗する() {
        JwtService issuer = new JwtService(properties());
        String token = issuer.issueAccessToken(userWithStore());

        JwtProperties otherProps = properties();
        otherProps.setSecret("different-secret-key-at-least-32-bytes-long!!");
        JwtService verifier = new JwtService(otherProps);

        assertThatThrownBy(() -> verifier.parseAccessToken(token)).isInstanceOf(JwtException.class);
    }
}
