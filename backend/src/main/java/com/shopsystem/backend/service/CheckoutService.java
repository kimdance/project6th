package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.AddPaymentRequest;
import com.shopsystem.backend.dto.ApplyDiscountRequest;
import com.shopsystem.backend.dto.CheckDiscountResponse;
import com.shopsystem.backend.dto.CheckLineResponse;
import com.shopsystem.backend.dto.CheckResponse;
import com.shopsystem.backend.dto.CheckTaxLineResponse;
import com.shopsystem.backend.dto.CreateCheckRequest;
import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.PaymentResponse;
import com.shopsystem.backend.dto.RefundRequest;
import com.shopsystem.backend.dto.RefundResponse;
import com.shopsystem.backend.entity.DiningTable;
import com.shopsystem.backend.entity.GuestCheck;
import com.shopsystem.backend.entity.GuestCheckDiscount;
import com.shopsystem.backend.entity.GuestCheckLine;
import com.shopsystem.backend.entity.GuestCheckTaxLine;
import com.shopsystem.backend.entity.OrderLine;
import com.shopsystem.backend.entity.Payment;
import com.shopsystem.backend.entity.PaymentMethodConfig;
import com.shopsystem.backend.entity.Refund;
import com.shopsystem.backend.entity.StoreSetting;
import com.shopsystem.backend.entity.TableSession;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.repository.DiningTableRepository;
import com.shopsystem.backend.repository.GuestCheckDiscountRepository;
import com.shopsystem.backend.repository.GuestCheckLineRepository;
import com.shopsystem.backend.repository.GuestCheckRepository;
import com.shopsystem.backend.repository.GuestCheckTaxLineRepository;
import com.shopsystem.backend.repository.OrderLineRepository;
import com.shopsystem.backend.repository.PaymentMethodConfigRepository;
import com.shopsystem.backend.repository.PaymentRepository;
import com.shopsystem.backend.repository.RefundRepository;
import com.shopsystem.backend.repository.ReservationRepository;
import com.shopsystem.backend.repository.StoreSettingRepository;
import com.shopsystem.backend.repository.TableSessionRepository;
import com.shopsystem.backend.repository.TableSessionTableRepository;
import com.shopsystem.backend.repository.UserRepository;
import com.shopsystem.backend.web.TenantContext;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 会計（チェック）の作成・値引き・決済・取消・返金（FR-G01〜G05・G06・G07・G07b・G10・G11）。
 * フェーズ1は決済代行との自動連携を行わず、すべての決済手段を手入力方式で扱う
 * （04_architecture.md 追補を参照。`02_requirements.md` §11.1 のとおり決済代行の契約自体が
 * 経営判断待りのため）。税額計算は {@link #recomputeTaxAndSubtotal} のコメントを参照。
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final Set<String> TAX_CATEGORIES = Set.of("STANDARD_10", "REDUCED_8");
    private static final Set<String> DISCOUNT_TYPES = Set.of("AMOUNT", "RATE", "COUPON", "ROUNDING");
    private static final Set<String> UNBILLABLE_SERVE_STATUSES = Set.of("CANCELLED", "REJECTED");

    private final GuestCheckRepository guestCheckRepository;
    private final GuestCheckLineRepository guestCheckLineRepository;
    private final GuestCheckDiscountRepository guestCheckDiscountRepository;
    private final GuestCheckTaxLineRepository guestCheckTaxLineRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OrderLineRepository orderLineRepository;
    private final TableSessionRepository tableSessionRepository;
    private final TableSessionTableRepository tableSessionTableRepository;
    private final DiningTableRepository diningTableRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentMethodConfigRepository paymentMethodConfigRepository;
    private final StoreSettingRepository storeSettingRepository;
    private final UserRepository userRepository;
    private final MessageSource messageSource;
    private final StoreAccessGuard accessGuard;
    private final AuditLogService auditLogService;

    public List<CheckResponse> listChecks(Long storeId, Long sessionId) {
        accessGuard.requireCanView(storeId);
        return guestCheckRepository.findAllByTableSession_IdOrderBySeqInSession(sessionId).stream()
                .map(this::toResponse)
                .toList();
    }

    public CheckResponse getCheck(Long storeId, Long checkId) {
        accessGuard.requireCanView(storeId);
        return toResponse(findCheck(storeId, checkId));
    }

    /** 会計の作成（FR-G01）。対象明細を省略すると、未割当の明細をすべて対象にする（FR-G11の別会計は明示指定）。 */
    @Transactional
    public CheckResponse createCheck(Long storeId, Long sessionId, CreateCheckRequest req) {
        accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanManageFloor(storeId);

        TableSession session = tableSessionRepository.findByIdAndStore_Id(sessionId, storeId)
                .orElseThrow(accessGuard::notFound);
        if (!"OPEN".equals(session.getStatus()) && !"BILLING".equals(session.getStatus())) {
            throw new BusinessException(List.of(err("checkout.error.session.not-billable", null)));
        }

        List<OrderLine> candidateLines = orderLineRepository.findAllByTableSession_IdOrderByRegisteredAt(sessionId)
                .stream()
                .filter(l -> !UNBILLABLE_SERVE_STATUSES.contains(l.getServeStatus()))
                .filter(l -> !guestCheckLineRepository.existsByOrderLine_Id(l.getId()))
                .toList();

        List<OrderLine> targetLines;
        if (req.getOrderLineIds() == null || req.getOrderLineIds().isEmpty()) {
            targetLines = candidateLines;
        } else {
            Map<Long, OrderLine> byId = new LinkedHashMap<>();
            candidateLines.forEach(l -> byId.put(l.getId(), l));
            targetLines = new ArrayList<>();
            for (Long id : req.getOrderLineIds()) {
                OrderLine line = byId.get(id);
                if (line == null) {
                    throw new BusinessException(
                            List.of(err("checkout.error.line.not-billable", "orderLineIds")));
                }
                targetLines.add(line);
            }
        }
        if (targetLines.isEmpty()) {
            throw new BusinessException(List.of(err("checkout.error.lines.required", null)));
        }

        TenantContext.Data ctx = TenantContext.get();
        String actor = currentActor(ctx.userId());
        LocalDateTime now = LocalDateTime.now();

        GuestCheck check = new GuestCheck();
        check.setTableSession(session);
        check.setStore(session.getStore());
        check.setSeqInSession(guestCheckRepository.countByTableSession_Id(sessionId) + 1);
        check.setStatus("OPEN");
        check.setBusinessDate(now.toLocalDate());
        check = guestCheckRepository.save(check);

        for (OrderLine line : targetLines) {
            GuestCheckLine checkLine = new GuestCheckLine();
            checkLine.setCheck(check);
            checkLine.setOrderLine(line);
            checkLine.setAmountJpy(line.getUnitPriceSnapJpy() * line.getQuantity());
            checkLine.setQuantity(line.getQuantity());
            guestCheckLineRepository.save(checkLine);
        }

        recomputeTaxAndSubtotal(check, targetLines);
        check.setTotalJpy(check.getSubtotalJpy() - check.getDiscountTotalJpy() + check.getTaxTotalJpy());
        guestCheckRepository.save(check);

        if ("OPEN".equals(session.getStatus())) {
            session.setStatus("BILLING");
            tableSessionRepository.save(session);
            tableSessionTableRepository.findFirstByTableSession_IdAndPrimaryTrue(sessionId)
                    .ifPresent(link -> {
                        DiningTable table = link.getDiningTable();
                        table.setStatus("BILLING");
                        diningTableRepository.save(table);
                    });
        }

        return toResponse(check);
    }

    /**
     * 税抜小計・税額の計算（FR-G01・G09。§6.4）。税区分ごとに明細の税込金額を合計し、税率で
     * 1回だけ逆算して税抜額と税額に分解する（明細単位では税額を計算しない）。値引きは、この
     * 税抜小計・税額には影響させず、確定金額（total）からのみ差し引く簡易方式とする
     * （インボイス対応の正式な内訳表示はレシート・領収書機能〈FR-G08・G09〉の実装時に見直す）。
     * 会計作成時に一度だけ計算し、以後（値引き追加時等）は再計算しない（明細は作成後に変更しない前提）。
     */
    private void recomputeTaxAndSubtotal(GuestCheck check, List<OrderLine> lines) {
        Map<String, Integer> grossByCategory = new LinkedHashMap<>();
        for (OrderLine line : lines) {
            String category = line.getTaxCategorySnap();
            int amount = line.getUnitPriceSnapJpy() * line.getQuantity();
            grossByCategory.merge(category, amount, Integer::sum);
        }

        int subtotal = 0;
        int taxTotal = 0;
        for (Map.Entry<String, Integer> entry : grossByCategory.entrySet()) {
            int rate = "STANDARD_10".equals(entry.getKey()) ? 10 : 8;
            int gross = entry.getValue();
            int exclusive = Math.floorDiv(gross * 100, 100 + rate);
            int tax = gross - exclusive;
            subtotal += exclusive;
            taxTotal += tax;

            GuestCheckTaxLine taxLine = new GuestCheckTaxLine();
            taxLine.setCheck(check);
            taxLine.setTaxCategory(entry.getKey());
            taxLine.setTaxableAmountJpy(exclusive);
            taxLine.setTaxAmountJpy(tax);
            guestCheckTaxLineRepository.save(taxLine);
        }
        check.setSubtotalJpy(subtotal);
        check.setTaxTotalJpy(taxTotal);
    }

    /** 値引き・クーポン・端数調整の追加（FR-G02）。会計確定前（OPEN）のみ可能。 */
    @Transactional
    public CheckResponse applyDiscount(Long storeId, Long checkId, ApplyDiscountRequest req) {
        accessGuard.requireStoreInTenant(storeId);
        GuestCheck check = findCheckEntity(storeId, checkId);
        StoreSetting setting = storeSettingRepository.findById(storeId).orElseThrow(accessGuard::notFound);
        accessGuard.requireCanAdjustCheck(storeId, setting.isRequireManagerApprovalForVoidRefund());

        if (!"OPEN".equals(check.getStatus())) {
            throw new BusinessException(List.of(err("checkout.error.check.not-open", null)));
        }
        if (req.getType() == null || !DISCOUNT_TYPES.contains(req.getType())) {
            throw new BusinessException(List.of(err("checkout.error.discount-type.invalid", "type")));
        }
        if (req.getValue() == null) {
            throw new BusinessException(List.of(err("checkout.error.discount-value.required", "value")));
        }
        boolean allowNegative = "ROUNDING".equals(req.getType());
        if (!allowNegative && req.getValue().signum() < 0) {
            throw new BusinessException(List.of(err("checkout.error.discount-value.invalid", "value")));
        }

        int currentRemaining = check.getSubtotalJpy() + check.getTaxTotalJpy() - check.getDiscountTotalJpy();
        int amountJpy;
        if ("RATE".equals(req.getType())) {
            if (req.getValue().compareTo(BigDecimal.ZERO) < 0 || req.getValue().compareTo(new BigDecimal(100)) > 0) {
                throw new BusinessException(List.of(err("checkout.error.discount-value.invalid", "value")));
            }
            amountJpy = req.getValue()
                    .multiply(BigDecimal.valueOf(currentRemaining))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .intValue();
        } else {
            amountJpy = req.getValue().setScale(0, RoundingMode.HALF_UP).intValue();
        }

        String beforeSummary = summarizeCheck(check);

        TenantContext.Data ctx = TenantContext.get();
        String actor = currentActor(ctx.userId());
        GuestCheckDiscount discount = new GuestCheckDiscount();
        discount.setCheck(check);
        discount.setType(req.getType());
        discount.setValue(req.getValue());
        discount.setAmountJpy(amountJpy);
        discount.setReason(trimToNull(req.getReason()));
        discount.setAppliedBy(actor);
        discount.setAppliedAt(LocalDateTime.now());
        guestCheckDiscountRepository.save(discount);

        check.setDiscountTotalJpy(check.getDiscountTotalJpy() + amountJpy);
        check.setTotalJpy(check.getSubtotalJpy() - check.getDiscountTotalJpy() + check.getTaxTotalJpy());
        if (check.getTotalJpy() < 0) {
            throw new BusinessException(List.of(err("checkout.error.discount-value.too-large", "value")));
        }
        guestCheckRepository.save(check);

        auditLogService.recordForCurrentUser(AuditActions.CHECK_DISCOUNT, storeId, "CHECK", check.getId(),
                beforeSummary, summarizeCheck(check));

        return toResponse(check);
    }

    /** 決済の記録（FR-G03・G05・G06・G07・G07b）。合計額に達したら自動的に会計を確定する。 */
    @Transactional
    public CheckResponse addPayment(Long storeId, Long checkId, AddPaymentRequest req) {
        accessGuard.requireStoreInTenant(storeId);
        accessGuard.requireCanManageFloor(storeId);
        GuestCheck check = findCheckEntity(storeId, checkId);

        if (!"OPEN".equals(check.getStatus())) {
            throw new BusinessException(List.of(err("checkout.error.check.not-open", null)));
        }
        String methodType = req.getMethodType();
        if (methodType == null || !PaymentMethodService.METHOD_TYPES.contains(methodType)) {
            throw new BusinessException(List.of(err("checkout.error.payment-method.invalid", "methodType")));
        }
        PaymentMethodConfig config = paymentMethodConfigRepository.findByStore_IdAndMethodType(storeId, methodType)
                .orElse(null);
        if (config == null || !config.isEnabled()) {
            throw new BusinessException(List.of(err("checkout.error.payment-method.disabled", "methodType")));
        }

        int paidSoFar = paymentRepository.findAllByCheck_IdOrderByProcessedAt(check.getId()).stream()
                .filter(p -> "SUCCESS".equals(p.getStatus()))
                .mapToInt(Payment::getAmountJpy)
                .sum();
        int balance = check.getTotalJpy() - paidSoFar;

        if (req.getAmountJpy() <= 0) {
            throw new BusinessException(List.of(err("checkout.error.payment-amount.invalid", "amountJpy")));
        }
        if (req.getAmountJpy() > balance) {
            throw new BusinessException(List.of(err("checkout.error.payment-amount.exceeds-balance", "amountJpy")));
        }

        TenantContext.Data ctx = TenantContext.get();
        String actor = currentActor(ctx.userId());
        LocalDateTime now = LocalDateTime.now();

        Payment payment = new Payment();
        payment.setCheck(check);
        payment.setMethodType(methodType);
        payment.setAmountJpy(req.getAmountJpy());
        payment.setStatus("SUCCESS");
        payment.setManualEntry(true);
        payment.setProcessedAt(now);
        payment.setProcessedBy(actor);
        if ("CASH".equals(methodType)) {
            int tendered = req.getTenderedJpy() != null ? req.getTenderedJpy() : req.getAmountJpy();
            if (tendered < req.getAmountJpy()) {
                throw new BusinessException(List.of(err("checkout.error.tendered.insufficient", "tenderedJpy")));
            }
            payment.setTenderedJpy(tendered);
            payment.setChangeJpy(tendered - req.getAmountJpy());
        }
        paymentRepository.save(payment);

        int newPaidTotal = paidSoFar + req.getAmountJpy();
        if (newPaidTotal >= check.getTotalJpy()) {
            String beforeSummary = summarizeCheck(check);
            check.setStatus("FINALIZED");
            check.setFinalizedAt(now);
            check.setFinalizedBy(actor);
            guestCheckRepository.save(check);
            auditLogService.recordForCurrentUser(AuditActions.CHECK_FINALIZE, storeId, "CHECK", check.getId(),
                    beforeSummary, summarizeCheck(check));
            closeSessionIfAllChecksSettled(storeId, check.getTableSession());
        }

        return toResponse(check);
    }

    /** 会計確定前の取消（FR-G10）。明細の会計割当を解除し、卓は営業中に戻る（別会計が残っていなければ）。 */
    @Transactional
    public CheckResponse voidCheck(Long storeId, Long checkId) {
        accessGuard.requireStoreInTenant(storeId);
        GuestCheck check = findCheckEntity(storeId, checkId);
        StoreSetting setting = storeSettingRepository.findById(storeId).orElseThrow(accessGuard::notFound);
        accessGuard.requireCanAdjustCheck(storeId, setting.isRequireManagerApprovalForVoidRefund());

        if (!"OPEN".equals(check.getStatus())) {
            throw new BusinessException(List.of(err("checkout.error.check.not-open", null)));
        }
        String beforeSummary = summarizeCheck(check);

        guestCheckLineRepository.deleteAllByCheck_Id(check.getId());
        guestCheckTaxLineRepository.deleteAllByCheck_Id(check.getId());
        check.setStatus("VOIDED");
        guestCheckRepository.save(check);

        auditLogService.recordForCurrentUser(AuditActions.CHECK_VOID, storeId, "CHECK", check.getId(),
                beforeSummary, summarizeCheck(check));

        TableSession session = check.getTableSession();
        long stillActive = guestCheckRepository.countByTableSession_IdAndStatusIn(
                session.getId(), List.of("OPEN", "FINALIZED"));
        if (stillActive == 0 && "BILLING".equals(session.getStatus())) {
            session.setStatus("OPEN");
            tableSessionRepository.save(session);
            tableSessionTableRepository.findFirstByTableSession_IdAndPrimaryTrue(session.getId())
                    .ifPresent(link -> {
                        DiningTable table = link.getDiningTable();
                        table.setStatus("OCCUPIED");
                        diningTableRepository.save(table);
                    });
        }

        return toResponse(check);
    }

    /** 会計確定後の返金（FR-G10）。会計本体（金額・明細）は不変で、返金イベントを追加するのみ。 */
    @Transactional
    public CheckResponse refund(Long storeId, Long checkId, RefundRequest req) {
        accessGuard.requireStoreInTenant(storeId);
        GuestCheck check = findCheckEntity(storeId, checkId);
        StoreSetting setting = storeSettingRepository.findById(storeId).orElseThrow(accessGuard::notFound);
        accessGuard.requireCanAdjustCheck(storeId, setting.isRequireManagerApprovalForVoidRefund());

        if (!"FINALIZED".equals(check.getStatus())) {
            throw new BusinessException(List.of(err("checkout.error.check.not-finalized", null)));
        }
        if (req.getAmountJpy() <= 0) {
            throw new BusinessException(List.of(err("checkout.error.payment-amount.invalid", "amountJpy")));
        }
        if (trimToNull(req.getReason()) == null) {
            throw new BusinessException(List.of(err("checkout.error.refund-reason.required", "reason")));
        }

        Payment payment = null;
        if (req.getPaymentId() != null) {
            payment = paymentRepository.findAllByCheck_IdOrderByProcessedAt(check.getId()).stream()
                    .filter(p -> p.getId().equals(req.getPaymentId()))
                    .findFirst()
                    .orElseThrow(accessGuard::notFound);
        }

        TenantContext.Data ctx = TenantContext.get();
        String actor = currentActor(ctx.userId());

        Refund refund = new Refund();
        refund.setCheck(check);
        refund.setPayment(payment);
        refund.setAmountJpy(req.getAmountJpy());
        refund.setReason(req.getReason());
        refund.setReasonNote(trimToNull(req.getReasonNote()));
        refund.setExecutedBy(actor);
        refund.setExecutedAt(LocalDateTime.now());
        refund.setApprovedBy("OWNER".equals(ctx.role()) || "MANAGER".equals(ctx.role()) ? actor : null);
        refundRepository.save(refund);

        auditLogService.recordForCurrentUser(AuditActions.CHECK_REFUND, storeId, "CHECK", check.getId(),
                summarizeCheck(check), "refundAmount=" + req.getAmountJpy() + ", reason=" + req.getReason());

        return toResponse(check);
    }

    /** 卓に紐づく全 check が FINALIZED（かつOPENが残っていない）なら、卓セッションをクローズする。 */
    private void closeSessionIfAllChecksSettled(Long storeId, TableSession session) {
        long openCount = guestCheckRepository.countByTableSession_IdAndStatusIn(session.getId(), List.of("OPEN"));
        if (openCount > 0) {
            return;
        }
        long finalizedCount = guestCheckRepository.countByTableSession_IdAndStatusIn(
                session.getId(), List.of("FINALIZED"));
        if (finalizedCount == 0) {
            return;
        }

        session.setStatus("CLOSED");
        session.setClosedAt(LocalDateTime.now());
        tableSessionRepository.save(session);

        tableSessionTableRepository.findFirstByTableSession_IdAndPrimaryTrue(session.getId())
                .ifPresent(link -> {
                    DiningTable table = link.getDiningTable();
                    table.setStatus("EMPTY");
                    diningTableRepository.save(table);
                });

        if (session.getReservationId() != null) {
            reservationRepository.findById(session.getReservationId()).ifPresent(reservation -> {
                if ("SEATED".equals(reservation.getStatus())) {
                    String before = "status=" + reservation.getStatus();
                    reservation.setStatus("DONE");
                    reservationRepository.save(reservation);
                    auditLogService.recordForCurrentUser(AuditActions.RESERVATION_CHANGE, storeId, "RESERVATION",
                            reservation.getId(), before, "status=DONE, tableSessionId=" + session.getId());
                }
            });
        }
    }

    private GuestCheck findCheckEntity(Long storeId, Long checkId) {
        return guestCheckRepository.findByIdAndStore_Id(checkId, storeId).orElseThrow(accessGuard::notFound);
    }

    private GuestCheck findCheck(Long storeId, Long checkId) {
        accessGuard.requireStoreInTenant(storeId);
        return findCheckEntity(storeId, checkId);
    }

    private String currentActor(Long userId) {
        return userRepository.findById(userId).map(u -> u.getEmail()).orElse("user:" + userId);
    }

    private String summarizeCheck(GuestCheck check) {
        return "status=" + check.getStatus() + ", subtotal=" + check.getSubtotalJpy()
                + ", discount=" + check.getDiscountTotalJpy() + ", tax=" + check.getTaxTotalJpy()
                + ", total=" + check.getTotalJpy();
    }

    private CheckResponse toResponse(GuestCheck check) {
        List<CheckLineResponse> lines = guestCheckLineRepository.findAllByCheck_Id(check.getId()).stream()
                .map(l -> new CheckLineResponse(
                        l.getId(), l.getOrderLine().getId(), l.getOrderLine().getItemNameSnap(),
                        l.getQuantity(), l.getAmountJpy()))
                .toList();
        List<CheckDiscountResponse> discounts = guestCheckDiscountRepository.findAllByCheck_Id(check.getId())
                .stream()
                .map(d -> new CheckDiscountResponse(
                        d.getId(), d.getType(), d.getValue(), d.getAmountJpy(), d.getReason(),
                        d.getAppliedBy(), d.getAppliedAt()))
                .toList();
        List<CheckTaxLineResponse> taxLines = guestCheckTaxLineRepository.findAllByCheck_Id(check.getId()).stream()
                .map(t -> new CheckTaxLineResponse(t.getTaxCategory(), t.getTaxableAmountJpy(), t.getTaxAmountJpy()))
                .toList();
        List<Payment> paymentEntities = paymentRepository.findAllByCheck_IdOrderByProcessedAt(check.getId());
        List<PaymentResponse> payments = paymentEntities.stream()
                .map(p -> new PaymentResponse(
                        p.getId(), p.getMethodType(), p.getAmountJpy(), p.getStatus(), p.getTenderedJpy(),
                        p.getChangeJpy(), p.getProcessedAt(), p.getProcessedBy()))
                .toList();
        List<RefundResponse> refunds = refundRepository.findAllByCheck_IdOrderByExecutedAt(check.getId()).stream()
                .map(r -> new RefundResponse(
                        r.getId(), r.getPayment() != null ? r.getPayment().getId() : null, r.getAmountJpy(),
                        r.getReason(), r.getReasonNote(), r.getExecutedBy(), r.getExecutedAt(), r.getApprovedBy()))
                .toList();
        int paidTotal = paymentEntities.stream()
                .filter(p -> "SUCCESS".equals(p.getStatus()))
                .mapToInt(Payment::getAmountJpy)
                .sum();

        return new CheckResponse(
                check.getId(), check.getTableSession().getId(), check.getSeqInSession(), check.getStatus(),
                check.getSubtotalJpy(), check.getDiscountTotalJpy(), check.getTaxTotalJpy(), check.getTotalJpy(),
                check.getSplitType(), check.getSplitCount(), check.getBusinessDate(), check.getFinalizedAt(),
                check.getFinalizedBy(), lines, discounts, taxLines, payments, refunds, paidTotal,
                check.getTotalJpy() - paidTotal);
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
