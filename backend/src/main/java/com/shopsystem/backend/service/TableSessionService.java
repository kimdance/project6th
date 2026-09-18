package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.OpenTableSessionRequest;
import com.shopsystem.backend.dto.TableSessionResponse;
import com.shopsystem.backend.entity.DiningTable;
import com.shopsystem.backend.entity.Reservation;
import com.shopsystem.backend.entity.Store;
import com.shopsystem.backend.entity.TableSession;
import com.shopsystem.backend.entity.TableSessionTable;
import com.shopsystem.backend.entity.TableSessionTableId;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.repository.DiningTableRepository;
import com.shopsystem.backend.repository.ReservationRepository;
import com.shopsystem.backend.repository.TableSessionRepository;
import com.shopsystem.backend.repository.TableSessionTableRepository;
import com.shopsystem.backend.repository.UserRepository;
import com.shopsystem.backend.web.TenantContext;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 卓セッション（来店）の管理（FR-E01・FR-C07。03_domain_model.md §4.2）。
 * 卓のクローズ（会計後）は会計・レジ（FR-G）の実装に合わせて追加する予定で、このサービスでは
 * オープンと一覧・詳細のみを扱う（04_architecture.md 追補を参照）。
 */
@Service
@RequiredArgsConstructor
public class TableSessionService {

    private static final List<String> ACTIVE_STATUSES = List.of("OPEN", "BILLING");

    private final TableSessionRepository tableSessionRepository;
    private final TableSessionTableRepository tableSessionTableRepository;
    private final DiningTableRepository diningTableRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final MessageSource messageSource;
    private final StoreAccessGuard accessGuard;
    private final AuditLogService auditLogService;

    /** 営業中（OPEN／BILLING）の卓セッション一覧（卓・注文画面の卓ボード用）。 */
    public List<TableSessionResponse> listActive(Long storeId) {
        accessGuard.requireCanView(storeId);
        return tableSessionRepository.findAllByStore_IdAndStatusInOrderByOpenedAt(storeId, ACTIVE_STATUSES).stream()
                .map(this::toResponse)
                .toList();
    }

    public TableSessionResponse get(Long storeId, Long sessionId) {
        accessGuard.requireCanView(storeId);
        TableSession session = tableSessionRepository.findByIdAndStore_Id(sessionId, storeId)
                .orElseThrow(accessGuard::notFound);
        return toResponse(session);
    }

    TableSession getEntity(Long storeId, Long sessionId) {
        return tableSessionRepository.findByIdAndStore_Id(sessionId, storeId).orElseThrow(accessGuard::notFound);
    }

    /**
     * 卓をオープンする（FR-E01）。予約からの来店割当（FR-C07）の場合は {@code reservationId} を
     * 指定し、対象予約をCONFIRMED→SEATEDへ遷移させる（03_domain_model.md §4.1）。
     */
    @Transactional
    public TableSessionResponse open(Long storeId, OpenTableSessionRequest req) {
        Store store = accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanManageFloor(storeId);

        List<ErrorItem> errors = new java.util.ArrayList<>();
        DiningTable table = null;
        if (req.getDiningTableId() == null) {
            errors.add(err("floor.error.table.required", "diningTableId"));
        } else {
            table = diningTableRepository.findByIdAndStore_Id(req.getDiningTableId(), storeId).orElse(null);
            if (table == null) {
                errors.add(err("floor.error.table.invalid", "diningTableId"));
            } else if (!"EMPTY".equals(table.getStatus())) {
                errors.add(err("floor.error.table.not-empty", "diningTableId"));
            }
        }
        if (req.getPartySize() <= 0) {
            errors.add(err("floor.error.party-size.invalid", "partySize"));
        }
        Reservation reservation = null;
        if (req.getReservationId() != null) {
            reservation = reservationRepository.findByIdAndStore_Id(req.getReservationId(), storeId).orElse(null);
            if (reservation == null) {
                errors.add(err("floor.error.reservation.invalid", "reservationId"));
            } else if (!"CONFIRMED".equals(reservation.getStatus())) {
                errors.add(err("floor.error.reservation.not-confirmed", "reservationId"));
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }

        TenantContext.Data ctx = TenantContext.get();
        String actor = currentActor(ctx.userId());
        LocalDateTime now = LocalDateTime.now();

        TableSession session = new TableSession();
        session.setCompanyCode(ctx.companyCode());
        session.setStore(store);
        session.setStatus("OPEN");
        session.setOpenedAt(now);
        session.setOpenedBy(actor);
        session.setPartySize(req.getPartySize());
        session.setReservationId(reservation != null ? reservation.getId() : null);
        session = tableSessionRepository.save(session);

        TableSessionTable link = new TableSessionTable();
        link.setId(new TableSessionTableId(session.getId(), table.getId()));
        link.setTableSession(session);
        link.setDiningTable(table);
        link.setPrimary(true);
        tableSessionTableRepository.save(link);

        table.setStatus("OCCUPIED");
        diningTableRepository.save(table);

        if (reservation != null) {
            String beforeSummary = "status=" + reservation.getStatus();
            reservation.setStatus("SEATED");
            reservationRepository.save(reservation);
            auditLogService.recordForCurrentUser(AuditActions.RESERVATION_CHANGE, storeId, "RESERVATION",
                    reservation.getId(), beforeSummary, "status=SEATED, tableSessionId=" + session.getId());
        }

        return toResponse(session, table);
    }

    private String currentActor(Long userId) {
        return userRepository.findById(userId).map(u -> u.getEmail()).orElse("user:" + userId);
    }

    private TableSessionResponse toResponse(TableSession session) {
        TableSessionTable link = tableSessionTableRepository
                .findFirstByTableSession_IdAndPrimaryTrue(session.getId())
                .orElseThrow(accessGuard::notFound);
        return toResponse(session, link.getDiningTable());
    }

    private TableSessionResponse toResponse(TableSession session, DiningTable table) {
        return new TableSessionResponse(
                session.getId(), table.getId(), table.getTableNo(), session.getStatus(), session.getPartySize(),
                session.getOpenedAt(), session.getOpenedBy(), session.getReservationId());
    }

    private ErrorItem err(String code, String field) {
        Locale locale = LocaleContextHolder.getLocale();
        return new ErrorItem(messageSource.getMessage(code, null, locale), List.of(field));
    }
}
