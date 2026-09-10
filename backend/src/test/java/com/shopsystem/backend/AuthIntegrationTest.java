package com.shopsystem.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

/**
 * GET /api/v1/auth/tenant・POST /api/v1/auth/login・POST /api/v1/auth/refresh の結合テスト
 * （04_architecture.md §6.1／§6.3）。ログイン失敗ロック・セッションタイムアウトは対象外（後回し）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private static final String HOST = "auth-test-co.localhost";
    private static final String OTHER_HOST = "auth-test-other.localhost";
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
        createCompanyWithOwner("auth-test-co", "認証テスト株式会社");
        createCompanyWithOwner("auth-test-other", "別のテナント株式会社");
    }

    @AfterEach
    void cleanup() {
        userRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
    }

    private void createCompanyWithOwner(String companyCode, String companyName) {
        Company company = new Company();
        company.setCompanyCode(companyCode);
        company.setName(companyName);
        company = companyRepository.save(company);

        User owner = new User();
        owner.setCompany(company);
        owner.setName("オーナー");
        owner.setEmail("owner@example.com");
        owner.setPassword(passwordEncoder.encode(PASSWORD));
        owner.setRole("OWNER");
        owner.setStatus("ACTIVE");
        userRepository.save(owner);
    }

    @Test
    void テナント解決_存在する会社は200() throws Exception {
        mvc.perform(get("/api/v1/auth/tenant").header("Host", HOST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyCode").value("auth-test-co"))
                .andExpect(jsonPath("$.companyName").value("認証テスト株式会社"));
    }

    @Test
    void テナント解決_存在しない会社は404() throws Exception {
        mvc.perform(get("/api/v1/auth/tenant").header("Host", "no-such-co.localhost"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ログイン成功_トークンが発行される() throws Exception {
        mvc.perform(login(HOST, "owner@example.com", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void ログイン失敗_パスワード不一致は401() throws Exception {
        mvc.perform(login(HOST, "owner@example.com", "wrong-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ログイン失敗_存在しないメールは401() throws Exception {
        mvc.perform(login(HOST, "nobody@example.com", PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ログイン失敗_存在しないサブドメインは404() throws Exception {
        mvc.perform(login("no-such-co.localhost", "owner@example.com", PASSWORD))
                .andExpect(status().isNotFound());
    }

    @Test
    void リフレッシュ_有効なトークンで新しいアクセストークンを得る() throws Exception {
        String refreshToken = loginAndExtractRefreshToken(HOST);

        mvc.perform(refresh(HOST, refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").value(refreshToken));
    }

    @Test
    void リフレッシュ_アクセストークンは使えない() throws Exception {
        String loginResponse = mvc.perform(login(HOST, "owner@example.com", PASSWORD))
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(loginResponse).get("accessToken").asText();

        mvc.perform(refresh(HOST, accessToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void リフレッシュ_不正なトークン文字列は401() throws Exception {
        mvc.perform(refresh(HOST, "not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void リフレッシュ_別テナントのサブドメインでは使えない() throws Exception {
        String refreshToken = loginAndExtractRefreshToken(HOST);

        mvc.perform(refresh(OTHER_HOST, refreshToken)).andExpect(status().isUnauthorized());
    }

    private String loginAndExtractRefreshToken(String host) throws Exception {
        String loginResponse = mvc.perform(login(host, "owner@example.com", PASSWORD))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(loginResponse);
        return node.get("refreshToken").asText();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String host, String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginPayload(email, password));
        return post("/api/v1/auth/login")
                .header("Host", host)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refresh(
            String host, String refreshToken) throws Exception {
        String body = objectMapper.writeValueAsString(new RefreshPayload(refreshToken));
        return post("/api/v1/auth/refresh")
                .header("Host", host)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private record LoginPayload(String email, String password) {
    }

    private record RefreshPayload(String refreshToken) {
    }
}
