package com.shopsystem.backend.repository;

import com.shopsystem.backend.entity.PaymentMethodConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentMethodConfigRepository extends JpaRepository<PaymentMethodConfig, Long> {

    List<PaymentMethodConfig> findAllByStore_Id(Long storeId);

    Optional<PaymentMethodConfig> findByStore_IdAndMethodType(Long storeId, String methodType);
}
