package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.CancelOrderLineRequest;
import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.OrderLineItemRequest;
import com.shopsystem.backend.dto.OrderLineResponse;
import com.shopsystem.backend.dto.RemakeOrderLineRequest;
import com.shopsystem.backend.dto.SubmitOrderRequest;
import com.shopsystem.backend.dto.TableSessionDetailResponse;
import com.shopsystem.backend.dto.UpdateOrderLineRequest;
import com.shopsystem.backend.entity.CustomerOrder;
import com.shopsystem.backend.entity.KitchenTicket;
import com.shopsystem.backend.entity.MenuItem;
import com.shopsystem.backend.entity.OrderLine;
import com.shopsystem.backend.entity.StoreSetting;
import com.shopsystem.backend.entity.TableSession;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.repository.CustomerOrderRepository;
import com.shopsystem.backend.repository.KitchenTicketRepository;
import com.shopsystem.backend.repository.MenuItemRepository;
import com.shopsystem.backend.repository.OrderLineRepository;
import com.shopsystem.backend.repository.StoreSettingRepository;
import com.shopsystem.backend.repository.UserRepository;
import com.shopsystem.backend.web.TenantContext;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 注文の入力・数量変更・取消・作り直し（FR-E02・E03・E03b・E03c。03_domain_model.md §4.5）。
 * オンライン専用の実装で、オフライン同期（NFR-05・04 §9）は対象外（04_architecture.md 追補を参照）。
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Set<String> VALID_CANCEL_REASONS =
            Set.of("ORDER_MISTAKE", "QUALITY", "DELAY", "WRONG_SERVE", "SOLD_OUT", "CUSTOMER", "OTHER");
    private static final Set<String> CANCELLABLE_BEFORE_SERVE = Set.of("PENDING", "PREPARING");

    private final TableSessionService tableSessionService;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderLineRepository orderLineRepository;
    private final KitchenTicketRepository kitchenTicketRepository;
    private final MenuItemRepository menuItemRepository;
    private final StoreSettingRepository storeSettingRepository;
    private final UserRepository userRepository;
    private final MessageSource messageSource;
    private final StoreAccessGuard accessGuard;
    private final AuditLogService auditLogService;

    /** 卓セッションの詳細（現在の注文明細一覧）。注文入力画面の初期表示・再取得に使う。 */
    public TableSessionDetailResponse getSessionDetail(Long storeId, Long sessionId) {
        var session = tableSessionService.get(storeId, sessionId);
        List<OrderLineResponse> lines = orderLineRepository.findAllByTableSession_IdOrderByRegisteredAt(sessionId)
                .stream().map(this::toLineResponse).toList();
        return new TableSessionDetailResponse(session, lines);
    }

    /** 注文を送信する（FR-E02）。STAFF入力は自動ACCEPTED、調理が要る明細があればキッチン伝票を発行する（FR-E04）。 */
    @Transactional
    public TableSessionDetailResponse submit(Long storeId, Long sessionId, SubmitOrderRequest req) {
        accessGuard.requireCanManageFloor(storeId);
        TableSession session = tableSessionService.getEntity(storeId, sessionId);
        if (!"OPEN".equals(session.getStatus())) {
            throw new BusinessException(List.of(err("floor.error.session.not-open", null)));
        }

        List<OrderLineItemRequest> requestedLines = req.getLines();
        if (requestedLines == null || requestedLines.isEmpty()) {
            throw new BusinessException(List.of(err("floor.error.lines.required", "lines")));
        }

        List<ErrorItem> errors = new ArrayList<>();
        List<MenuItem> menuItems = new ArrayList<>();
        for (int i = 0; i < requestedLines.size(); i++) {
            OrderLineItemRequest line = requestedLines.get(i);
            String field = "lines[" + i + "]";
            if (line.getQuantity() <= 0) {
                errors.add(err("floor.error.quantity.invalid", field));
            }
            MenuItem item = line.getMenuItemId() == null
                    ? null : menuItemRepository.findByIdAndStore_Id(line.getMenuItemId(), storeId).orElse(null);
            if (item == null) {
                errors.add(err("floor.error.menu-item.invalid", field));
            } else if (!"ON_SALE".equals(item.getSalesStatus()) || !item.isActive()) {
                errors.add(err("floor.error.menu-item.unavailable", field));
            }
            menuItems.add(item);
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }

        TenantContext.Data ctx = TenantContext.get();
        String actor = currentActor(ctx.userId());
        LocalDateTime now = LocalDateTime.now();
        LocalDate businessDate = now.toLocalDate();

        CustomerOrder order = new CustomerOrder();
        order.setTableSession(session);
        order.setSource("STAFF");
        order.setEnteredBy(actor);
        order.setStatus("ACCEPTED");
        order.setSubmittedAt(now);
        order.setAcceptedBy(actor);
        order.setAcceptedAt(now);
        order = customerOrderRepository.save(order);

        boolean needsKitchenTicket = false;
        for (int i = 0; i < requestedLines.size(); i++) {
            OrderLineItemRequest req0 = requestedLines.get(i);
            MenuItem item = menuItems.get(i);
            OrderLine line = new OrderLine();
            line.setOrder(order);
            line.setTableSession(session);
            line.setMenuItem(item);
            line.setItemNameSnap(item.getName());
            line.setUnitPriceSnapJpy(item.getPriceJpy());
            line.setTaxCategorySnap(item.getTaxCategory());
            line.setQuantity(req0.getQuantity());
            line.setNote(trimToNull(req0.getNote()));
            line.setServeStatus("PENDING");
            line.setRegisteredAt(now);
            line.setRegisteredBy(actor);
            line.setBusinessDate(businessDate);
            orderLineRepository.save(line);
            if ("COOK".equals(item.getPrepType())) {
                needsKitchenTicket = true;
            }
        }

        if (needsKitchenTicket) {
            KitchenTicket ticket = new KitchenTicket();
            ticket.setOrder(order);
            ticket.setStore(session.getStore());
            ticket.setStatus("NEW");
            kitchenTicketRepository.save(ticket);
        }

        return getSessionDetail(storeId, sessionId);
    }

    /** 数量・メモの変更（FR-E03）。まだ提供前（PENDING）の明細のみ変更できる。 */
    @Transactional
    public OrderLineResponse updateLine(Long storeId, Long lineId, UpdateOrderLineRequest req) {
        accessGuard.requireCanManageFloor(storeId);
        OrderLine line = findLine(storeId, lineId);

        if (!"PENDING".equals(line.getServeStatus())) {
            throw new BusinessException(List.of(err("floor.error.line.not-pending", null)));
        }
        if (req.getQuantity() <= 0) {
            throw new BusinessException(List.of(err("floor.error.quantity.invalid", "quantity")));
        }
        line.setQuantity(req.getQuantity());
        line.setNote(trimToNull(req.getNote()));
        orderLineRepository.save(line);
        return toLineResponse(line);
    }

    /**
     * 明細の取消（FR-E03・E03b）。提供前は卓・注文の権限があれば誰でも、提供後（SERVED）は
     * 店舗設定「要店長承認」に応じて権限を絞る（FR-J01により監査ログ必須）。
     */
    @Transactional
    public OrderLineResponse cancelLine(Long storeId, Long lineId, CancelOrderLineRequest req) {
        OrderLine line = findLine(storeId, lineId);
        boolean wasServed = "SERVED".equals(line.getServeStatus());

        if (wasServed) {
            StoreSetting setting = storeSettingRepository.findById(storeId).orElseThrow(accessGuard::notFound);
            accessGuard.requireCanCancelServedLine(storeId, setting.isRequireManagerApprovalForServeCancel());
        } else {
            accessGuard.requireCanManageFloor(storeId);
            if (!CANCELLABLE_BEFORE_SERVE.contains(line.getServeStatus())) {
                throw new BusinessException(List.of(err("floor.error.line.not-cancellable", null)));
            }
        }

        if (req.getReason() == null || !VALID_CANCEL_REASONS.contains(req.getReason())) {
            throw new BusinessException(List.of(err("floor.error.cancel-reason.invalid", "reason")));
        }

        StoreSetting setting = storeSettingRepository.findById(storeId).orElseThrow(accessGuard::notFound);
        boolean chargeable = req.getChargeable() != null ? req.getChargeable()
                : "CUSTOMER".equals(req.getReason())
                        ? setting.isCancelChargeDefaultCustomer() : setting.isCancelChargeDefaultStore();

        String beforeSummary = wasServed ? summarizeLine(line) : null;

        line.setServeStatus("CANCELLED");
        line.setCancelledAt(LocalDateTime.now());
        line.setCancelledBy(currentActor(TenantContext.get().userId()));
        line.setCancelReason(req.getReason());
        line.setWasCooked(req.getWasCooked() != null ? req.getWasCooked() : Boolean.FALSE);
        line.setCancelChargeable(chargeable);
        orderLineRepository.save(line);

        if (wasServed) {
            auditLogService.recordForCurrentUser(AuditActions.ORDER_LINE_CANCEL_AFTER_SERVE, storeId, "ORDER_LINE",
                    line.getId(), beforeSummary, summarizeLine(line));
        }

        closeTicketIfOrderDone(line.getOrder().getId());
        return toLineResponse(line);
    }

    /** 作り直し（FR-E03c）。取消済み（CANCELLED）の明細を元に、新規明細を作成し remakeOfLineId で関連付ける。 */
    @Transactional
    public OrderLineResponse remakeLine(Long storeId, Long lineId, RemakeOrderLineRequest req) {
        accessGuard.requireCanManageFloor(storeId);
        OrderLine original = findLine(storeId, lineId);
        if (!"CANCELLED".equals(original.getServeStatus())) {
            throw new BusinessException(List.of(err("floor.error.line.not-cancelled", null)));
        }

        int quantity = req.getQuantity() != null && req.getQuantity() > 0 ? req.getQuantity() : original.getQuantity();
        String note = req.getNote() != null ? trimToNull(req.getNote()) : original.getNote();

        TenantContext.Data ctx = TenantContext.get();
        String actor = currentActor(ctx.userId());
        LocalDateTime now = LocalDateTime.now();

        CustomerOrder order = new CustomerOrder();
        order.setTableSession(original.getTableSession());
        order.setSource("STAFF");
        order.setEnteredBy(actor);
        order.setStatus("ACCEPTED");
        order.setSubmittedAt(now);
        order.setAcceptedBy(actor);
        order.setAcceptedAt(now);
        order = customerOrderRepository.save(order);

        MenuItem item = original.getMenuItem();
        OrderLine remake = new OrderLine();
        remake.setOrder(order);
        remake.setTableSession(original.getTableSession());
        remake.setMenuItem(item);
        remake.setItemNameSnap(item.getName());
        remake.setUnitPriceSnapJpy(item.getPriceJpy());
        remake.setTaxCategorySnap(item.getTaxCategory());
        remake.setQuantity(quantity);
        remake.setNote(note);
        remake.setServeStatus("PENDING");
        remake.setRegisteredAt(now);
        remake.setRegisteredBy(actor);
        remake.setBusinessDate(now.toLocalDate());
        remake.setRemakeOfLine(original);
        orderLineRepository.save(remake);

        if ("COOK".equals(item.getPrepType())) {
            KitchenTicket ticket = new KitchenTicket();
            ticket.setOrder(order);
            ticket.setStore(original.getTableSession().getStore());
            ticket.setStatus("NEW");
            kitchenTicketRepository.save(ticket);
        }

        return toLineResponse(remake);
    }

    /** 提供済みにする（FR-E07）。KDS画面は未実装のため、ホールがこの操作で提供状態を記録する。 */
    @Transactional
    public OrderLineResponse markServed(Long storeId, Long lineId) {
        accessGuard.requireCanManageFloor(storeId);
        OrderLine line = findLine(storeId, lineId);
        if (!CANCELLABLE_BEFORE_SERVE.contains(line.getServeStatus())) {
            throw new BusinessException(List.of(err("floor.error.line.not-servable", null)));
        }
        line.setServeStatus("SERVED");
        line.setServedAt(LocalDateTime.now());
        orderLineRepository.save(line);

        closeTicketIfOrderDone(line.getOrder().getId());
        return toLineResponse(line);
    }

    /** 対象注文の全明細が終端状態（SERVED/CANCELLED/REJECTED）になったら、キッチン伝票をDONEにする（04 §9）。 */
    private void closeTicketIfOrderDone(Long orderId) {
        List<OrderLine> lines = orderLineRepository.findAllByOrder_Id(orderId);
        boolean allDone = lines.stream()
                .allMatch(l -> Set.of("SERVED", "CANCELLED", "REJECTED").contains(l.getServeStatus()));
        if (!allDone) {
            return;
        }
        kitchenTicketRepository.findFirstByOrder_Id(orderId).ifPresent(ticket -> {
            if (!"DONE".equals(ticket.getStatus())) {
                ticket.setStatus("DONE");
                kitchenTicketRepository.save(ticket);
            }
        });
    }

    private OrderLine findLine(Long storeId, Long lineId) {
        return orderLineRepository.findByIdAndTableSession_Store_Id(lineId, storeId)
                .orElseThrow(accessGuard::notFound);
    }

    private String currentActor(Long userId) {
        return userRepository.findById(userId).map(u -> u.getEmail()).orElse("user:" + userId);
    }

    private String summarizeLine(OrderLine line) {
        return "itemName=" + line.getItemNameSnap() + ", quantity=" + line.getQuantity()
                + ", serveStatus=" + line.getServeStatus();
    }

    private OrderLineResponse toLineResponse(OrderLine line) {
        return new OrderLineResponse(
                line.getId(), line.getOrder().getId(), line.getMenuItem().getId(), line.getItemNameSnap(),
                line.getUnitPriceSnapJpy(), line.getTaxCategorySnap(), line.getQuantity(), line.getNote(),
                line.getServeStatus(), line.getRegisteredAt(), line.getRegisteredBy(), line.getServedAt(),
                line.getCancelledAt(), line.getCancelledBy(), line.getCancelReason(), line.getCancelChargeable(),
                line.getWasCooked(), line.getRemakeOfLine() != null ? line.getRemakeOfLine().getId() : null);
    }

    private ErrorItem err(String code, String field) {
        Locale locale = LocaleContextHolder.getLocale();
        return new ErrorItem(
                messageSource.getMessage(code, null, locale), field == null ? List.of() : List.of(field));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
