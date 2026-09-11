package com.shopsystem.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsystem.backend.entity.Company;
import com.shopsystem.backend.entity.Store;
import com.shopsystem.backend.entity.StoreSetting;
import com.shopsystem.backend.entity.User;
import com.shopsystem.backend.repository.CompanyRepository;
import com.shopsystem.backend.repository.StoreRepository;
import com.shopsystem.backend.repository.StoreSettingRepository;
import com.shopsystem.backend.repository.UserRepository;
import com.shopsystem.backend.security.JwtService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Set;

/**
 * POST /api/v1/stores・GET/PUT /api/v1/stores/{storeId}/settings の結合テスト（FR-B01・FR-B03）。
 * あわせて JwtAuthenticationInterceptor（04_architecture.md §3.2）の関所としての振る舞いも検証する。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class StoreSettingsIntegrationTest {

    private static final String HOST = "store-test-co.localhost";
    private static final String OTHER_HOST = "store-test-other.localhost";

    @Autowired
    MockMvc mvc;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    StoreRepository storeRepository;

    @Autowired
    StoreSettingRepository storeSettingRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Company company;
    private User owner;
    private User hallStaff;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setCompanyCode("store-test-co");
        company.setName("店舗設定テスト株式会社");
        company = companyRepository.save(company);

        owner = newUser(company, "owner@example.com", "OWNER", null);
        hallStaff = newUser(company, "hall@example.com", "HALL", null);
    }

    @AfterEach
    void cleanup() {
        // users.store_id が store を参照するため、store 削除前に users を消す。
        userRepository.deleteAllInBatch();
        storeSettingRepository.deleteAllInBatch();
        storeRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
    }

    private User newUser(Company company, String email, String role, Store store) {
        User user = new User();
        user.setCompany(company);
        user.setStores(store == null ? Set.of() : Set.of(store));
        user.setName(role);
        user.setEmail(email);
        user.setPassword("{bcrypt}dummy");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return userRepository.save(user);
    }

    @Test
    void オーナーは店舗を作成できる() throws Exception {
        mvc.perform(createStore(owner, "本店", 20))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("本店"))
                .andExpect(jsonPath("$.seatCount").value(20));
    }

    @Test
    void 現場スタッフは店舗を作成できず403() throws Exception {
        mvc.perform(createStore(hallStaff, "本店", 20)).andExpect(status().isForbidden());
    }

    @Test
    void 認証ヘッダなしは401() throws Exception {
        String body = objectMapper.writeValueAsString(new StoreCreatePayload("本店", 20));
        mvc.perform(post("/api/v1/stores").header("Host", HOST)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 別テナントのサブドメインでトークンを使うと401() throws Exception {
        Company other = new Company();
        other.setCompanyCode("store-test-other");
        other.setName("別テナント");
        companyRepository.save(other);

        String body = objectMapper.writeValueAsString(new StoreCreatePayload("本店", 20));
        mvc.perform(post("/api/v1/stores").header("Host", OTHER_HOST)
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 作成した店舗の設定を閲覧し更新できる() throws Exception {
        Long storeId = createStoreAndGetId(owner, "本店", 20);

        mvc.perform(getSettings(owner, storeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("本店"))
                .andExpect(jsonPath("$.taxRounding").value("FLOOR"));

        String updateBody = objectMapper.writeValueAsString(
                new StoreSettingsPayload("本店（改称）", 30, "CEIL", false, "T1234567890123"));
        mvc.perform(put("/api/v1/stores/" + storeId + "/settings")
                        .header("Host", HOST)
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("本店（改称）"))
                .andExpect(jsonPath("$.taxRounding").value("CEIL"))
                .andExpect(jsonPath("$.priceIncludesTax").value(false));

        Store persisted = storeRepository.findById(storeId).orElseThrow();
        assertThat(persisted.getSeatCount()).isEqualTo(30);
        StoreSetting setting = storeSettingRepository.findById(storeId).orElseThrow();
        assertThat(setting.getInvoiceRegNo()).isEqualTo("T1234567890123");
    }

    @Test
    void 店長は自店の設定のみ編集できる() throws Exception {
        Long ownStoreId = createStoreAndGetId(owner, "自店", 10);
        Long otherStoreId = createStoreAndGetId(owner, "他店", 10);
        User manager = newUser(company, "manager@example.com", "MANAGER",
                storeRepository.findById(ownStoreId).orElseThrow());

        String body = objectMapper.writeValueAsString(new StoreSettingsPayload("自店（更新）", 12, "FLOOR", true, null));
        mvc.perform(put("/api/v1/stores/" + ownStoreId + "/settings")
                        .header("Host", HOST)
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mvc.perform(put("/api/v1/stores/" + otherStoreId + "/settings")
                        .header("Host", HOST)
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void 他社の店舗idを指定すると404() throws Exception {
        Company other = new Company();
        other.setCompanyCode("intruder-co");
        other.setName("侵入者株式会社");
        other = companyRepository.save(other);

        Store otherStore = new Store();
        otherStore.setCompany(companyRepository.findById(other.getId()).orElseThrow());
        otherStore.setName("他社の店");
        otherStore.setSeatCount(5);
        otherStore = storeRepository.save(otherStore);
        StoreSetting otherSetting = new StoreSetting();
        otherSetting.setStore(otherStore);
        storeSettingRepository.save(otherSetting);

        mvc.perform(getSettings(owner, otherStore.getId())).andExpect(status().isNotFound());

        storeSettingRepository.delete(otherSetting);
        storeRepository.delete(otherStore);
        companyRepository.delete(other);
    }

    @Test
    void 店名が空なら400() throws Exception {
        String body = objectMapper.writeValueAsString(new StoreCreatePayload("", 10));
        mvc.perform(post("/api/v1/stores").header("Host", HOST)
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].fields[0]").value("name"));
    }

    private Long createStoreAndGetId(User asUser, String name, int seatCount) throws Exception {
        String response = mvc.perform(createStore(asUser, name, seatCount))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.get("id").asLong();
    }

    private MockHttpServletRequestBuilder createStore(User asUser, String name, int seatCount) throws Exception {
        String body = objectMapper.writeValueAsString(new StoreCreatePayload(name, seatCount));
        return post("/api/v1/stores")
                .header("Host", HOST)
                .header("Authorization", "Bearer " + jwtService.issueAccessToken(asUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private MockHttpServletRequestBuilder getSettings(User asUser, Long storeId) {
        return get("/api/v1/stores/" + storeId + "/settings")
                .header("Host", HOST)
                .header("Authorization", "Bearer " + jwtService.issueAccessToken(asUser));
    }

    private record StoreCreatePayload(String name, int seatCount) {
    }

    private record StoreSettingsPayload(
            String name, int seatCount, String taxRounding, boolean priceIncludesTax, String invoiceRegNo) {
    }
}
