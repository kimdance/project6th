package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    // company_code は小文字正規化して保存するので、そのまま等価比較でよい（04_architecture.md §3.1）。
    boolean existsByCompanyCode(String companyCode);

    Optional<Company> findByCompanyCode(String companyCode);
}
