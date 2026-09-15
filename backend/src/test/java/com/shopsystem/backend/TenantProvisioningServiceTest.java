package com.shopsystem.backend;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shopsystem.backend.dto.TenantProvisioningRequest;
import com.shopsystem.backend.entity.Company;
import com.shopsystem.backend.exception.ConflictException;
import com.shopsystem.backend.repository.CompanyRepository;
import com.shopsystem.backend.repository.UserRepository;
import com.shopsystem.backend.service.TenantProvisioningService;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * existsByCompanyCode の事前チェックと保存の間で他リクエストが同じ company_code を
 * 先に確定させた場合（競合）、DB の UNIQUE 制約違反を 409 へ変換できることを確認する。
 */
class TenantProvisioningServiceTest {

    @Test
    void 事前チェック通過後にDB側で重複が判明したら409() {
        CompanyRepository companyRepository = mock(CompanyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.setUseCodeAsDefaultMessage(true);

        when(companyRepository.existsByCompanyCode("race-co")).thenReturn(false);
        when(companyRepository.saveAndFlush(any(Company.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}hashed");

        TenantProvisioningService service = new TenantProvisioningService(
                companyRepository, userRepository, passwordEncoder, messageSource);

        TenantProvisioningRequest req = new TenantProvisioningRequest();
        req.setCompanyCode("race-co");
        req.setCompanyName("会社");
        req.setOwnerName("氏名");
        req.setOwnerEmail("a@example.com");
        req.setPassword("secret123");

        assertThatThrownBy(() -> service.provision(req)).isInstanceOf(ConflictException.class);
    }
}
