package com.shopsystem.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsystem.backend.entity.Company;
import com.shopsystem.backend.entity.Store;
import com.shopsystem.backend.entity.StoreSetting;
import com.shopsystem.backend.entity.User;
import com.shopsystem.backend.repository.AuditLogRepository;
import com.shopsystem.backend.repository.CompanyRepository;
import com.shopsystem.backend.repository.StoreRepository;
import com.shopsystem.backend.repository.StoreSettingRepository;
import com.shopsystem.backend.repository.UserRepository;
import com.shopsystem.backend.security.JwtService;
import com.shopsystem.backend.service.AuditActions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

/**
 * 監査ログ（FR-J01・FR-J04）の結合テスト。店舗設定の変更が記録され、経営管理者（全店）・
 * 店長（自店のみ）が GET /api/v1/audit-logs で検索・閲覧できることを検証する。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class AuditLogIntegrationTest {

    private static final String HOST = "audit-test-co.localhost";

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
    AuditLogRepository auditLogRepository;

    @Autowired
    JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Company company;
    private User owner;
    private User hallStaff;
    private User manager;
    private Store store;
    private Store otherStore;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setCompanyCode("audit-test-co");
        company.setName("監査ログテスト株式会社");
        company = companyRepository.save(company);

        owner = newUser("owner@example.com", "OWNER");
        hallStaff = newUser("hall@example.com", "HALL");

        store = new Store();
        store.setCompany(company);
        store.setName("本店");
        store.setSeatCount(20);
        store = storeRepository.save(store);

        StoreSetting setting = new StoreSetting();
        setting.setStore(store);
        storeSettingRepository.save(setting);

        otherStore = new Store();
        otherStore.setCompany(company);
        otherStore.setName("他店");
        otherStore.setSeatCount(10);
        otherStore = storeRepository.save(otherStore);

        StoreSetting otherSetting = new StoreSetting();
        otherSetting.setStore(otherStore);
        storeSettingRepository.save(otherSetting);

        // 「本店」のみ担当する店長（「他店」の操作・店舗に紐づかない全社共通操作は見えないはず）。
        manager = newUser("manager@example.com", "MANAGER", store);
    }

    @AfterEach
    void cleanup() {
        auditLogRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        storeSettingRepository.deleteAllInBatch();
        storeRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
    }

    private User newUser(String email, String role, Store... stores) {
        User user = new User();
        user.setCompany(company);
        user.setStores(Set.of(stores));
        user.setName(role);
        user.setEmail(email);
        user.setPassword("{bcrypt}dummy");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return userRepository.save(user);
    }

    @Test
    void 店舗設定を変更すると監査ログに記録され経営管理者が閲覧できる() throws Exception {
        String body = objectMapper.writeValueAsString(
                new StoreSettingsPayload("本店（改称）", 30, "CEIL", false, null, "INSTANT", false, true));
        mvc.perform(put("/api/v1/stores/" + store.getId() + "/settings")
                        .header("Host", HOST)
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.findAll()).hasSize(1);
        var log = auditLogRepository.findAll().get(0);
        assertThat(log.getAction()).isEqualTo(AuditActions.STORE_SETTING_CHANGE);
        assertThat(log.getActor()).isEqualTo("owner@example.com");
        assertThat(log.getBeforeSummary()).contains("name=本店");
        assertThat(log.getAfterSummary()).contains("name=本店（改称）");

        mvc.perform(get("/api/v1/audit-logs")
                        .header("Host", HOST)
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value(AuditActions.STORE_SETTING_CHANGE))
                .andExpect(jsonPath("$.content[0].actor").value("owner@example.com"));
    }

    @Test
    void 現場スタッフは監査ログを閲覧できず403() throws Exception {
        mvc.perform(get("/api/v1/audit-logs")
                        .header("Host", HOST)
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(hallStaff)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 操作種別で絞り込める() throws Exception {
        String body = objectMapper.writeValueAsString(
                new StoreSettingsPayload("本店", 20, "FLOOR", true, null, "APPROVAL", true, true));
        mvc.perform(put("/api/v1/stores/" + store.getId() + "/settings")
                        .header("Host", HOST)
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/audit-logs")
                        .header("Host", HOST)
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(owner))
                        .param("action", "USER_REGISTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/v1/audit-logs")
                        .header("Host", HOST)
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(owner))
                        .param("action", AuditActions.STORE_SETTING_CHANGE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void 店長は自分の所属店舗の操作のみ閲覧でき全社共通操作や他店の操作は見えない() throws Exception {
        // 自店（本店）の設定変更 → 店長からも見えるはず。
        String ownStoreBody = objectMapper.writeValueAsString(
                new StoreSettingsPayload("本店（改称）", 20, "FLOOR", true, null, "APPROVAL", true, true));
        mvc.perform(put("/api/v1/stores/" + store.getId() + "/settings")
                        .header("Host", HOST)
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(ownStoreBody))
                .andExpect(status().isOk());

        // 他店の設定変更 → 店長からは見えないはず。
        String otherStoreBody = objectMapper.writeValueAsString(
                new StoreSettingsPayload("他店（改称）", 10, "FLOOR", true, null, "APPROVAL", true, true));
        mvc.perform(put("/api/v1/stores/" + otherStore.getId() + "/settings")
                        .header("Host", HOST)
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(otherStoreBody))
                .andExpect(status().isOk());

        // オーナーには自店・他店の設定変更、合わせて2件見えるはず。
        mvc.perform(get("/api/v1/audit-logs")
                        .header("Host", HOST)
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // 店長には自店の設定変更（1件）だけが見えるはず（他店の変更・ログインは対象外）。
        mvc.perform(get("/api/v1/audit-logs")
                        .header("Host", HOST)
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].storeId").value(store.getId()))
                .andExpect(jsonPath("$.content[0].action").value(AuditActions.STORE_SETTING_CHANGE));
    }

    @Test
    void 店長が自分の所属店舗以外を指定すると403() throws Exception {
        mvc.perform(get("/api/v1/audit-logs")
                        .header("Host", HOST)
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(manager))
                        .param("storeId", String.valueOf(otherStore.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void 所属店舗が無い店長は空の一覧が返る() throws Exception {
        User managerWithoutStore = newUser("manager2@example.com", "MANAGER");

        mvc.perform(get("/api/v1/audit-logs")
                        .header("Host", HOST)
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(managerWithoutStore)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private record StoreSettingsPayload(
            String name, int seatCount, String taxRounding, boolean priceIncludesTax, String invoiceRegNo,
            String webReservationMode, boolean cancelChargeDefaultCustomer, boolean cancelChargeDefaultStore) {
    }
}
