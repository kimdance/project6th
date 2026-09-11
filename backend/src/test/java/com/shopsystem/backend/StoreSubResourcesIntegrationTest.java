package com.shopsystem.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsystem.backend.entity.Company;
import com.shopsystem.backend.entity.PaymentMethodConfig;
import com.shopsystem.backend.entity.Store;
import com.shopsystem.backend.entity.User;
import com.shopsystem.backend.repository.CompanyRepository;
import com.shopsystem.backend.repository.DiningTableRepository;
import com.shopsystem.backend.repository.PaymentMethodConfigRepository;
import com.shopsystem.backend.repository.StoreBusinessDayRepository;
import com.shopsystem.backend.repository.StoreRepository;
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

import java.util.List;

/**
 * 卓（FR-B02）・決済手段（FR-B04/B05）・営業日（FR-B07）の結合テスト。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class StoreSubResourcesIntegrationTest {

    private static final String HOST = "sub-test-co.localhost";

    @Autowired
    MockMvc mvc;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    StoreRepository storeRepository;

    @Autowired
    DiningTableRepository diningTableRepository;

    @Autowired
    PaymentMethodConfigRepository paymentMethodConfigRepository;

    @Autowired
    StoreBusinessDayRepository storeBusinessDayRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User owner;
    private User hallStaff;
    private Store store;

    @BeforeEach
    void setUp() {
        Company company = new Company();
        company.setCompanyCode("sub-test-co");
        company.setName("サブリソーステスト株式会社");
        company = companyRepository.save(company);

        owner = newUser(company, "owner@example.com", "OWNER");
        hallStaff = newUser(company, "hall@example.com", "HALL");

        store = new Store();
        store.setCompany(company);
        store.setName("本店");
        store.setSeatCount(20);
        store = storeRepository.save(store);
    }

    @AfterEach
    void cleanup() {
        userRepository.deleteAllInBatch();
        storeBusinessDayRepository.deleteAllInBatch();
        paymentMethodConfigRepository.deleteAllInBatch();
        diningTableRepository.deleteAllInBatch();
        storeRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
    }

    private User newUser(Company company, String email, String role) {
        User user = new User();
        user.setCompany(company);
        user.setName(role);
        user.setEmail(email);
        user.setPassword("{bcrypt}dummy");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return userRepository.save(user);
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder builder) {
        return builder.header("Host", HOST).header("Authorization", "Bearer " + jwtService.issueAccessToken(user));
    }

    // ---- 卓（FR-B02） ----

    @Test
    void オーナーは卓を作成できQRトークンが発行される() throws Exception {
        String body = objectMapper.writeValueAsString(new TablePayload("T1", 4, "TABLE", "1階", true));
        mvc.perform(as(owner, post("/api/v1/stores/" + store.getId() + "/tables"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableNo").value("T1"))
                .andExpect(jsonPath("$.qrToken").isNotEmpty())
                .andExpect(jsonPath("$.status").value("EMPTY"));
    }

    @Test
    void 卓番号が重複すると409() throws Exception {
        String body = objectMapper.writeValueAsString(new TablePayload("T1", 4, "TABLE", null, true));
        mvc.perform(as(owner, post("/api/v1/stores/" + store.getId() + "/tables"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(as(owner, post("/api/v1/stores/" + store.getId() + "/tables"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void 現場スタッフは卓を作成できず403() throws Exception {
        String body = objectMapper.writeValueAsString(new TablePayload("T1", 4, "TABLE", null, true));
        mvc.perform(as(hallStaff, post("/api/v1/stores/" + store.getId() + "/tables"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void 卓の一覧取得と更新ができる() throws Exception {
        String createBody = objectMapper.writeValueAsString(new TablePayload("T1", 4, "TABLE", null, true));
        String created = mvc.perform(as(owner, post("/api/v1/stores/" + store.getId() + "/tables"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andReturn().getResponse().getContentAsString();
        Long tableId = objectMapper.readTree(created).get("id").asLong();

        mvc.perform(as(owner, get("/api/v1/stores/" + store.getId() + "/tables")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tableNo").value("T1"));

        String updateBody = objectMapper.writeValueAsString(new TablePayload("T1-改", 6, "TABLE", "2階", false));
        mvc.perform(as(owner, put("/api/v1/stores/" + store.getId() + "/tables/" + tableId))
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableNo").value("T1-改"))
                .andExpect(jsonPath("$.seatCount").value(6))
                .andExpect(jsonPath("$.active").value(false));
    }

    // ---- 決済手段（FR-B04・FR-B05） ----

    @Test
    void 決済手段の一覧は4種類が既定で無効として返る() throws Exception {
        mvc.perform(as(owner, get("/api/v1/stores/" + store.getId() + "/payment-methods")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].methodType").value("CASH"))
                .andExpect(jsonPath("$[0].enabled").value(false))
                .andExpect(jsonPath("$[0].hasCredential").value(false));
    }

    @Test
    void 決済手段を有効化し接続情報を設定すると平文は返らない() throws Exception {
        String body = objectMapper.writeValueAsString(
                new PaymentMethodPayload(true, "PayPay", "merchant-secret-key", null));
        mvc.perform(as(owner, put("/api/v1/stores/" + store.getId() + "/payment-methods/PAYPAY"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.hasCredential").value(true))
                .andExpect(jsonPath("$..credential").doesNotExist());

        PaymentMethodConfig saved = paymentMethodConfigRepository
                .findByStore_IdAndMethodType(store.getId(), "PAYPAY").orElseThrow();
        assertThat(saved.getCredentialEnc()).isNotEqualTo("merchant-secret-key".getBytes());
        assertThat(new String(saved.getCredentialEnc())).doesNotContain("merchant-secret-key");
    }

    @Test
    void 空文字の接続情報で削除できる() throws Exception {
        String setBody = objectMapper.writeValueAsString(new PaymentMethodPayload(true, null, "secret", null));
        mvc.perform(as(owner, put("/api/v1/stores/" + store.getId() + "/payment-methods/PAYPAY"))
                        .contentType(MediaType.APPLICATION_JSON).content(setBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCredential").value(true));

        String clearBody = objectMapper.writeValueAsString(new PaymentMethodPayload(true, null, "", null));
        mvc.perform(as(owner, put("/api/v1/stores/" + store.getId() + "/payment-methods/PAYPAY"))
                        .contentType(MediaType.APPLICATION_JSON).content(clearBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCredential").value(false));
    }

    @Test
    void 不正な決済手段の種類は400() throws Exception {
        String body = objectMapper.writeValueAsString(new PaymentMethodPayload(true, null, null, null));
        mvc.perform(as(owner, put("/api/v1/stores/" + store.getId() + "/payment-methods/BITCOIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    // ---- 営業日（FR-B07） ----

    @Test
    void 曜日ごとの営業日を一括登録し取得できる() throws Exception {
        String body = objectMapper.writeValueAsString(List.of(
                new WeeklyPayload(1, true, "17:00", "23:00", 30),
                new WeeklyPayload(0, false, null, null, null)));
        mvc.perform(as(owner, put("/api/v1/stores/" + store.getId() + "/business-days/weekly"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(as(owner, get("/api/v1/stores/" + store.getId() + "/business-days")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekly.length()").value(2))
                .andExpect(jsonPath("$.weekly[0].weekday").value(0))
                .andExpect(jsonPath("$.weekly[0].open").value(false))
                .andExpect(jsonPath("$.weekly[1].weekday").value(1))
                .andExpect(jsonPath("$.weekly[1].openTime").value("17:00:00"));
    }

    @Test
    void 曜日が重複すると400() throws Exception {
        String body = objectMapper.writeValueAsString(List.of(
                new WeeklyPayload(1, true, null, null, null),
                new WeeklyPayload(1, false, null, null, null)));
        mvc.perform(as(owner, put("/api/v1/stores/" + store.getId() + "/business-days/weekly"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 臨時休業を追加し削除できる() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ExceptionPayload("2026-12-31", false, null, null, null));
        String created = mvc.perform(as(owner, post("/api/v1/stores/" + store.getId() + "/business-days/exceptions"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessDate").value("2026-12-31"))
                .andReturn().getResponse().getContentAsString();
        Long exceptionId = objectMapper.readTree(created).get("id").asLong();

        // 同じ日付は重複登録できない。
        mvc.perform(as(owner, post("/api/v1/stores/" + store.getId() + "/business-days/exceptions"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        mvc.perform(as(owner,
                        delete("/api/v1/stores/" + store.getId() + "/business-days/exceptions/" + exceptionId)))
                .andExpect(status().isNoContent());

        mvc.perform(as(owner, get("/api/v1/stores/" + store.getId() + "/business-days")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exceptions.length()").value(0));
    }

    private record TablePayload(String tableNo, int seatCount, String seatType, String area, boolean active) {
    }

    private record PaymentMethodPayload(boolean enabled, String displayName, String credential, String note) {
    }

    private record WeeklyPayload(
            Integer weekday, boolean open, String openTime, String closeTime, Integer reservationCapacity) {
    }

    private record ExceptionPayload(
            String businessDate, boolean open, String openTime, String closeTime, Integer reservationCapacity) {
    }
}
