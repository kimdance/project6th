package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.TaxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {

    List<TaxRate> findAllByStore_IdOrderByTaxCategoryAscEffectiveFromDesc(Long storeId);

    /** ある区分・ある日付時点で有効な税率（effectiveFromが最も新しい、今日以前の行）。 */
    Optional<TaxRate> findFirstByStore_IdAndTaxCategoryAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            Long storeId, String taxCategory, LocalDate asOf);

    Optional<TaxRate> findByIdAndStore_Id(Long id, Long storeId);

    boolean existsByStore_IdAndTaxCategoryAndEffectiveFrom(Long storeId, String taxCategory, LocalDate effectiveFrom);
}
