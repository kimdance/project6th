package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.PaymentMethodResponse;
import com.shopsystem.backend.dto.PaymentMethodUpdateRequest;
import com.shopsystem.backend.entity.PaymentMethodConfig;
import com.shopsystem.backend.entity.Store;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.repository.PaymentMethodConfigRepository;
import com.shopsystem.backend.security.CredentialCryptoService;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 決済手段の有効化・接続情報の管理（FR-B04・FR-B05。04_architecture.md §4.4・§6.3）。
 * 接続情報は {@link CredentialCryptoService} で暗号化して保存し、平文はレスポンスに含めない。
 * method_type は固定4種（CASH／PAYPAY／CREDIT_CARD／RAKUTEN_PAY）のため、未設定の店舗にも
 * 既定値（無効・未設定）で一覧に含めて返す。
 */
@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    public static final List<String> METHOD_TYPES = List.of("CASH", "PAYPAY", "CREDIT_CARD", "RAKUTEN_PAY");

    private final PaymentMethodConfigRepository paymentMethodConfigRepository;
    private final MessageSource messageSource;
    private final StoreAccessGuard accessGuard;
    private final CredentialCryptoService credentialCryptoService;

    public List<PaymentMethodResponse> list(Long storeId) {
        accessGuard.requireStoreInTenant(storeId);

        Map<String, PaymentMethodConfig> existing = new LinkedHashMap<>();
        for (PaymentMethodConfig config : paymentMethodConfigRepository.findAllByStore_Id(storeId)) {
            existing.put(config.getMethodType(), config);
        }

        return METHOD_TYPES.stream()
                .map(type -> existing.containsKey(type)
                        ? toResponse(existing.get(type))
                        : new PaymentMethodResponse(type, false, null, null, false))
                .toList();
    }

    @Transactional
    public PaymentMethodResponse update(Long storeId, String methodType, PaymentMethodUpdateRequest req) {
        Store store = accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanEdit(storeId);

        String type = methodType == null ? null : methodType.toUpperCase(Locale.ROOT);
        if (!METHOD_TYPES.contains(type)) {
            throw new BusinessException(List.of(
                    err("payment-method.error.type.invalid", "methodType")));
        }

        PaymentMethodConfig config = paymentMethodConfigRepository.findByStore_IdAndMethodType(storeId, type)
                .orElseGet(() -> {
                    PaymentMethodConfig c = new PaymentMethodConfig();
                    c.setStore(store);
                    c.setMethodType(type);
                    return c;
                });

        config.setEnabled(req.isEnabled());
        config.setDisplayName(trimToNull(req.getDisplayName()));
        config.setNote(trimToNull(req.getNote()));

        if (req.getCredential() != null) {
            // 空文字は削除、非空は暗号化して保存。null（未指定）は既存値を保持する。
            config.setCredentialEnc(
                    req.getCredential().isEmpty() ? null : credentialCryptoService.encrypt(req.getCredential()));
        }

        config = paymentMethodConfigRepository.save(config);
        return toResponse(config);
    }

    private PaymentMethodResponse toResponse(PaymentMethodConfig config) {
        return new PaymentMethodResponse(
                config.getMethodType(), config.isEnabled(), config.getDisplayName(), config.getNote(),
                config.getCredentialEnc() != null);
    }

    private ErrorItem err(String code, String field) {
        Locale locale = LocaleContextHolder.getLocale();
        return new ErrorItem(messageSource.getMessage(code, null, locale), List.of(field));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
