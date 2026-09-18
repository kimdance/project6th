import { authedFetch, extractErrors, type ErrorItem } from './http';

/** OPEN / FINALIZED / VOIDED（03_domain_model.md §4.6） */
export type CheckStatus = 'OPEN' | 'FINALIZED' | 'VOIDED';

/** CASH / PAYPAY / CREDIT_CARD / RAKUTEN_PAY */
export type PaymentMethodType = 'CASH' | 'PAYPAY' | 'CREDIT_CARD' | 'RAKUTEN_PAY';

/** AMOUNT / RATE / COUPON / ROUNDING */
export type DiscountType = 'AMOUNT' | 'RATE' | 'COUPON' | 'ROUNDING';

export interface CheckLine {
  id: number;
  orderLineId: number;
  itemNameSnap: string;
  quantity: number;
  amountJpy: number;
}

export interface CheckDiscount {
  id: number;
  type: DiscountType;
  value: number;
  amountJpy: number;
  reason: string | null;
  appliedBy: string;
  appliedAt: string;
}

export interface CheckTaxLine {
  taxCategory: string;
  taxableAmountJpy: number;
  taxAmountJpy: number;
}

export interface CheckPayment {
  id: number;
  methodType: PaymentMethodType;
  amountJpy: number;
  status: string;
  tenderedJpy: number | null;
  changeJpy: number | null;
  processedAt: string;
  processedBy: string;
}

export interface CheckRefund {
  id: number;
  paymentId: number | null;
  amountJpy: number;
  reason: string;
  reasonNote: string | null;
  executedBy: string;
  executedAt: string;
  approvedBy: string | null;
}

export interface GuestCheck {
  id: number;
  tableSessionId: number;
  seqInSession: number;
  status: CheckStatus;
  subtotalJpy: number;
  discountTotalJpy: number;
  taxTotalJpy: number;
  totalJpy: number;
  splitType: string;
  splitCount: number | null;
  businessDate: string;
  finalizedAt: string | null;
  finalizedBy: string | null;
  lines: CheckLine[];
  discounts: CheckDiscount[];
  taxLines: CheckTaxLine[];
  payments: CheckPayment[];
  refunds: CheckRefund[];
  paidTotalJpy: number;
  balanceJpy: number;
}

export interface PaymentMethod {
  methodType: PaymentMethodType;
  enabled: boolean;
  displayName: string | null;
  note: string | null;
  hasCredential: boolean;
}

type Result<T> = { ok: true; data: T } | { ok: false; errors: ErrorItem[] };

export async function fetchPaymentMethods(storeId: number): Promise<PaymentMethod[]> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/payment-methods`);
  if (!res.ok) {
    return [];
  }
  return (await res.json()) as PaymentMethod[];
}

export async function fetchChecks(storeId: number, sessionId: number): Promise<GuestCheck[]> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/table-sessions/${sessionId}/checks`);
  if (!res.ok) {
    return [];
  }
  return (await res.json()) as GuestCheck[];
}

export async function createCheck(
  storeId: number,
  sessionId: number,
  orderLineIds?: number[]
): Promise<Result<GuestCheck>> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/table-sessions/${sessionId}/checks`, {
    method: 'POST',
    body: JSON.stringify({ orderLineIds: orderLineIds ?? null }),
  });
  if (res.ok) {
    return { ok: true, data: (await res.json()) as GuestCheck };
  }
  return { ok: false, errors: await extractErrors(res) };
}

export async function applyDiscount(
  storeId: number,
  checkId: number,
  body: { type: DiscountType; value: number; reason: string }
): Promise<Result<GuestCheck>> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/checks/${checkId}/discounts`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, data: (await res.json()) as GuestCheck };
  }
  return { ok: false, errors: await extractErrors(res) };
}

export async function addPayment(
  storeId: number,
  checkId: number,
  body: { methodType: PaymentMethodType; amountJpy: number; tenderedJpy?: number }
): Promise<Result<GuestCheck>> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/checks/${checkId}/payments`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, data: (await res.json()) as GuestCheck };
  }
  return { ok: false, errors: await extractErrors(res) };
}

export async function voidCheck(storeId: number, checkId: number): Promise<Result<GuestCheck>> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/checks/${checkId}/void`, {
    method: 'POST',
  });
  if (res.ok) {
    return { ok: true, data: (await res.json()) as GuestCheck };
  }
  return { ok: false, errors: await extractErrors(res) };
}

export async function refundCheck(
  storeId: number,
  checkId: number,
  body: { paymentId: number | null; amountJpy: number; reason: string; reasonNote: string }
): Promise<Result<GuestCheck>> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/checks/${checkId}/refunds`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, data: (await res.json()) as GuestCheck };
  }
  return { ok: false, errors: await extractErrors(res) };
}
