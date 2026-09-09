package com.shopsystem.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopsystem.backend.entity.Company;
import com.shopsystem.backend.repository.CompanyRepository;
import com.shopsystem.backend.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class SignupIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    UserRepository userRepository;

    @AfterEach
    void cleanup() {
        userRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
    }

    @Test
    void 正常系_companyとowner作成_companyCodeとemailは小文字化_パスワードはハッシュ化() throws Exception {
        String body = """
                {"companyCode":"Acme-Izakaya","companyName":"アクメ居酒屋","ownerName":"山田太郎",
                 "ownerEmail":"Owner@Example.com","password":"secret123"}
                """;

        mvc.perform(post("/api/v1/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyCode").value("acme-izakaya"));

        Company company = companyRepository.findByCompanyCode("acme-izakaya").orElseThrow();
        assertThat(company.getName()).isEqualTo("アクメ居酒屋");
        assertThat(company.getContractStatus()).isEqualTo("ACTIVE");

        var owner = userRepository.findByCompany_IdAndEmail(company.getId(), "owner@example.com").orElseThrow();
        assertThat(owner.getName()).isEqualTo("山田太郎");
        assertThat(owner.getRole()).isEqualTo("OWNER");
        assertThat(owner.getStatus()).isEqualTo("ACTIVE");
        assertThat(owner.getStore()).isNull();
        assertThat(owner.getPassword()).startsWith("{bcrypt}");
        assertThat(owner.getPassword()).isNotEqualTo("secret123");
    }

    @Test
    void 会社コードが重複したら409() throws Exception {
        String body = """
                {"companyCode":"dup-co","companyName":"最初の会社","ownerName":"一人目",
                 "ownerEmail":"first@example.com","password":"secret123"}
                """;
        mvc.perform(post("/api/v1/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        String body2 = """
                {"companyCode":"DUP-CO","companyName":"あとから来た会社","ownerName":"二人目",
                 "ownerEmail":"second@example.com","password":"secret123"}
                """;
        mvc.perform(post("/api/v1/signup").contentType(MediaType.APPLICATION_JSON).content(body2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        assertThat(companyRepository.count()).isEqualTo(1);
    }

    @Test
    void 会社コードの形式が不正なら400() throws Exception {
        String body = """
                {"companyCode":"bad_code","companyName":"会社","ownerName":"氏名",
                 "ownerEmail":"a@example.com","password":"secret123"}
                """;
        mvc.perform(post("/api/v1/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].fields[0]").value("companyCode"));

        assertThat(companyRepository.count()).isZero();
    }

    @Test
    void 予約語の会社コードは400() throws Exception {
        String body = """
                {"companyCode":"admin","companyName":"会社","ownerName":"氏名",
                 "ownerEmail":"a@example.com","password":"secret123"}
                """;
        mvc.perform(post("/api/v1/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].fields[0]").value("companyCode"));
    }

    @Test
    void パスワードが短いと400_複数エラーをまとめて返す() throws Exception {
        String body = """
                {"companyCode":"x","companyName":"","ownerName":"",
                 "ownerEmail":"not-an-email","password":"short"}
                """;
        mvc.perform(post("/api/v1/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(5));
    }
}
