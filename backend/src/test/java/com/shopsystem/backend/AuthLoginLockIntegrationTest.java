package com.shopsystem.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsystem.backend.entity.Company;
import com.shopsystem.backend.entity.User;
import com.shopsystem.backend.repository.CompanyRepository;
import com.shopsystem.backend.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * ログイン失敗の連続回数による一時ロック（FR-A08）の結合テスト。
 * しきい値を3回に下げて検証する（既定値5回のままだと手数が増えるだけで検証内容は同じため）。
 */
@SpringBootTest(properties = {
        "app.auth.max-failed-attempts=3",
        "app.auth.lock-duration-minutes=15"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class AuthLoginLockIntegrationTest {

    private static final String HOST = "lock-test-co.localhost";
    private static final String EMAIL = "owner@example.com";
    private static final String PASSWORD = "secret123";

    @Autowired
    MockMvc mvc;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        Company company = new Company();
        company.setCompanyCode("lock-test-co");
        company.setName("ロックテスト株式会社");
        company = companyRepository.save(company);

        User owner = new User();
        owner.setCompany(company);
        owner.setName("オーナー");
        owner.setEmail(EMAIL);
        owner.setPassword(passwordEncoder.encode(PASSWORD));
        owner.setRole("OWNER");
        owner.setStatus("ACTIVE");
        userRepository.save(owner);
    }

    @AfterEach
    void cleanup() {
        userRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
    }

    @Test
    void しきい値回数の失敗でロックされ正しいパスワードでも拒否される() throws Exception {
        mvc.perform(login(EMAIL, "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(login(EMAIL, "wrong")).andExpect(status().isUnauthorized());
        // 3回目の失敗でロックされる。
        mvc.perform(login(EMAIL, "wrong")).andExpect(status().isUnauthorized());

        User locked = userRepository.findByCompany_IdAndEmail(companyId(), EMAIL).orElseThrow();
        assertThat(locked.getStatus()).isEqualTo("LOCKED");
        assertThat(locked.getLockedUntil()).isNotNull();

        // ロック中は正しいパスワードでも拒否される。
        mvc.perform(login(EMAIL, PASSWORD)).andExpect(status().isUnauthorized());
    }

    @Test
    void しきい値未満で成功すれば失敗回数はリセットされる() throws Exception {
        mvc.perform(login(EMAIL, "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(login(EMAIL, PASSWORD)).andExpect(status().isOk());

        User user = userRepository.findByCompany_IdAndEmail(companyId(), EMAIL).orElseThrow();
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getStatus()).isEqualTo("ACTIVE");

        // リセットされているため、再び連続2回の失敗ではロックされない。
        mvc.perform(login(EMAIL, "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(login(EMAIL, PASSWORD)).andExpect(status().isOk());
    }

    private Long companyId() {
        return companyRepository.findByCompanyCode("lock-test-co").orElseThrow().getId();
    }

    private MockHttpServletRequestBuilder login(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginPayload(email, password));
        return post("/api/v1/auth/login")
                .header("Host", HOST)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private record LoginPayload(String email, String password) {
    }
}
