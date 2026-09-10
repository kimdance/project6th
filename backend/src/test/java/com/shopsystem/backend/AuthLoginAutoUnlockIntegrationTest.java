package com.shopsystem.backend;

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
 * ロック時間経過後、次回アクセス時にアプリ層が自動解除することの結合テスト（FR-A08）。
 * lock-duration-minutes=0 として「ロック直後には期限が過ぎている」状態を作り、実時間を待たずに検証する。
 */
@SpringBootTest(properties = {
        "app.auth.max-failed-attempts=2",
        "app.auth.lock-duration-minutes=0"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class AuthLoginAutoUnlockIntegrationTest {

    private static final String HOST = "unlock-test-co.localhost";
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
        company.setCompanyCode("unlock-test-co");
        company.setName("自動解除テスト株式会社");
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
    void ロック期限が過ぎていれば次回アクセスで自動解除され正しいパスワードでログインできる() throws Exception {
        mvc.perform(login("wrong")).andExpect(status().isUnauthorized());
        // 2回目の失敗でロック（lock-duration-minutes=0のため即座に期限切れ）。
        mvc.perform(login("wrong")).andExpect(status().isUnauthorized());

        mvc.perform(login(PASSWORD)).andExpect(status().isOk());

        User user = userRepository.findByCompany_IdAndEmail(
                companyRepository.findByCompanyCode("unlock-test-co").orElseThrow().getId(), EMAIL)
                .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(user.getStatus()).isEqualTo("ACTIVE");
        org.assertj.core.api.Assertions.assertThat(user.getFailedLoginCount()).isZero();
    }

    private MockHttpServletRequestBuilder login(String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginPayload(EMAIL, password));
        return post("/api/v1/auth/login")
                .header("Host", HOST)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private record LoginPayload(String email, String password) {
    }
}
