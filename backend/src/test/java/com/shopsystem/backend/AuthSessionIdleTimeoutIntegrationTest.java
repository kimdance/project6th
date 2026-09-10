package com.shopsystem.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
 * 無操作セッションタイムアウト（FR-A09）の結合テスト。
 * idle-timeout-minutes=0 として「ログイン直後にはもう期限が過ぎている」状態を作り、実時間を待たずに検証する。
 */
@SpringBootTest(properties = "app.session.idle-timeout-minutes=0")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class AuthSessionIdleTimeoutIntegrationTest {

    private static final String HOST = "idle-test-co.localhost";
    private static final String OWNER_EMAIL = "owner@example.com";
    private static final String HALL_EMAIL = "hall@example.com";
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
        company.setCompanyCode("idle-test-co");
        company.setName("タイムアウトテスト株式会社");
        company = companyRepository.save(company);

        User owner = new User();
        owner.setCompany(company);
        owner.setName("オーナー");
        owner.setEmail(OWNER_EMAIL);
        owner.setPassword(passwordEncoder.encode(PASSWORD));
        owner.setRole("OWNER");
        owner.setStatus("ACTIVE");
        userRepository.save(owner);

        User hallStaff = new User();
        hallStaff.setCompany(company);
        hallStaff.setName("ホールスタッフ");
        hallStaff.setEmail(HALL_EMAIL);
        hallStaff.setPassword(passwordEncoder.encode(PASSWORD));
        hallStaff.setRole("HALL");
        hallStaff.setStatus("ACTIVE");
        userRepository.save(hallStaff);
    }

    @AfterEach
    void cleanup() {
        userRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
    }

    @Test
    void オーナーは無操作時間が上限を超えるとリフレッシュを拒否される() throws Exception {
        String refreshToken = loginAndExtractRefreshToken(OWNER_EMAIL);

        // idle-timeout-minutes=0 のため、ログイン直後の1回目のリフレッシュで即座にタイムアウト扱いになる。
        mvc.perform(refresh(refreshToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void 現場スタッフはタイムアウト対象外でリフレッシュできる() throws Exception {
        String refreshToken = loginAndExtractRefreshToken(HALL_EMAIL);

        // idle-timeout-minutes=0 でも、HALL はオフライン注文の運用（§9）と衝突するため対象外。
        mvc.perform(refresh(refreshToken)).andExpect(status().isOk());
    }

    private String loginAndExtractRefreshToken(String email) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginPayload(email, PASSWORD));
        String response = mvc.perform(post("/api/v1/auth/login")
                        .header("Host", HOST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.get("refreshToken").asText();
    }

    private MockHttpServletRequestBuilder refresh(String refreshToken) throws Exception {
        String body = objectMapper.writeValueAsString(new RefreshPayload(refreshToken));
        return post("/api/v1/auth/refresh")
                .header("Host", HOST)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private record LoginPayload(String email, String password) {
    }

    private record RefreshPayload(String refreshToken) {
    }
}
