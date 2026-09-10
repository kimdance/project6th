package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.TableRequest;
import com.shopsystem.backend.dto.TableResponse;
import com.shopsystem.backend.entity.DiningTable;
import com.shopsystem.backend.entity.Store;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.exception.ConflictException;
import com.shopsystem.backend.repository.DiningTableRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 卓（テーブル）マスタの管理（FR-B02。04_architecture.md §4.4・§6.3）。
 * qr_token はモバイルオーダー用QRの識別子で、サーバがランダムに発番する（クライアントの自己申告は認めない）。
 */
@Service
@RequiredArgsConstructor
public class TableService {

    private final DiningTableRepository diningTableRepository;
    private final MessageSource messageSource;
    private final StoreAccessGuard accessGuard;

    public List<TableResponse> list(Long storeId) {
        accessGuard.requireStoreInTenant(storeId);
        return diningTableRepository.findAllByStore_IdOrderByTableNo(storeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TableResponse create(Long storeId, TableRequest req) {
        Store store = accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanEdit(storeId);

        String tableNo = validate(storeId, req, null);

        if (diningTableRepository.existsByStore_IdAndTableNo(storeId, tableNo)) {
            throw new ConflictException(
                    messageSource.getMessage("table.error.table-no.duplicate", null, LocaleContextHolder.getLocale()));
        }

        DiningTable table = new DiningTable();
        table.setStore(store);
        table.setTableNo(tableNo);
        table.setSeatCount(req.getSeatCount());
        table.setArea(trimToNull(req.getArea()));
        table.setActive(req.isActive());
        table.setQrToken(generateUniqueQrToken(storeId));
        table = diningTableRepository.save(table);

        return toResponse(table);
    }

    @Transactional
    public TableResponse update(Long storeId, Long tableId, TableRequest req) {
        accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanEdit(storeId);

        DiningTable table = diningTableRepository.findByIdAndStore_Id(tableId, storeId)
                .orElseThrow(accessGuard::notFound);

        String tableNo = validate(storeId, req, tableId);

        if (!tableNo.equals(table.getTableNo())
                && diningTableRepository.existsByStore_IdAndTableNo(storeId, tableNo)) {
            throw new ConflictException(
                    messageSource.getMessage("table.error.table-no.duplicate", null, LocaleContextHolder.getLocale()));
        }

        table.setTableNo(tableNo);
        table.setSeatCount(req.getSeatCount());
        table.setArea(trimToNull(req.getArea()));
        table.setActive(req.isActive());
        diningTableRepository.save(table);

        return toResponse(table);
    }

    private String validate(Long storeId, TableRequest req, Long ignoredTableId) {
        List<ErrorItem> errors = new ArrayList<>();
        String tableNo = trimToNull(req.getTableNo());
        if (tableNo == null) {
            errors.add(err("table.error.table-no.required", "tableNo"));
        }
        if (req.getSeatCount() < 0) {
            errors.add(err("table.error.seat-count.invalid", "seatCount"));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }
        return tableNo;
    }

    private String generateUniqueQrToken(Long storeId) {
        String token;
        do {
            token = UUID.randomUUID().toString().replace("-", "");
        } while (diningTableRepository.existsByStore_IdAndQrToken(storeId, token));
        return token;
    }

    private TableResponse toResponse(DiningTable table) {
        return new TableResponse(
                table.getId(), table.getTableNo(), table.getSeatCount(), table.getArea(),
                table.getQrToken(), table.getStatus(), table.isActive());
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
