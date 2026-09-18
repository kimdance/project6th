import { authedFetch, extractErrors, type ErrorItem } from './http';

/** OPEN / BILLING / CLOSED（卓セッションの状態。03_domain_model.md §4.2） */
export type TableSessionStatus = 'OPEN' | 'BILLING' | 'CLOSED';

/** PENDING / PREPARING / SERVED / CANCELLED / REJECTED（03_domain_model.md §4.5） */
export type ServeStatus = 'PENDING' | 'PREPARING' | 'SERVED' | 'CANCELLED' | 'REJECTED';

/** ORDER_MISTAKE / QUALITY / DELAY / WRONG_SERVE / SOLD_OUT / CUSTOMER / OTHER */
export type CancelReason = 'ORDER_MISTAKE' | 'QUALITY' | 'DELAY' | 'WRONG_SERVE' | 'SOLD_OUT' | 'CUSTOMER' | 'OTHER';

/** GET/POST /api/v1/stores/{storeId}/table-sessions のレスポンス（FR-E01）。 */
export interface TableSession {
  id: number;
  diningTableId: number;
  tableNo: string;
  status: TableSessionStatus;
  partySize: number;
  openedAt: string;
  openedBy: string;
  reservationId: number | null;
}

export interface OrderLine {
  id: number;
  orderId: number;
  menuItemId: number;
  itemNameSnap: string;
  unitPriceSnapJpy: number;
  taxCategorySnap: string;
  quantity: number;
  note: string | null;
  serveStatus: ServeStatus;
  registeredAt: string;
  registeredBy: string;
  servedAt: string | null;
  cancelledAt: string | null;
  cancelledBy: string | null;
  cancelReason: CancelReason | null;
  cancelChargeable: boolean | null;
  wasCooked: boolean | null;
  remakeOfLineId: number | null;
}

export interface TableSessionDetail {
  session: TableSession;
  lines: OrderLine[];
}

export interface OpenTableSessionRequest {
  diningTableId: number;
  partySize: number;
  reservationId: number | null;
}

export interface OrderLineItemRequest {
  menuItemId: number;
  quantity: number;
  note: string;
}

export interface CancelOrderLineRequest {
  reason: CancelReason;
  wasCooked: boolean;
  chargeable?: boolean;
}

type Result<T> = { ok: true; data: T } | { ok: false; errors: ErrorItem[] };

export async function fetchActiveTableSessions(storeId: number): Promise<TableSession[]> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/table-sessions`);
  if (!res.ok) {
    return [];
  }
  return (await res.json()) as TableSession[];
}

export async function openTableSession(
  storeId: number,
  body: OpenTableSessionRequest
): Promise<Result<TableSession>> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/table-sessions`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, data: (await res.json()) as TableSession };
  }
  return { ok: false, errors: await extractErrors(res) };
}

export async function fetchTableSessionDetail(
  storeId: number,
  sessionId: number
): Promise<TableSessionDetail | null> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/table-sessions/${sessionId}`);
  if (!res.ok) {
    return null;
  }
  return (await res.json()) as TableSessionDetail;
}

export async function submitOrder(
  storeId: number,
  sessionId: number,
  lines: OrderLineItemRequest[]
): Promise<Result<TableSessionDetail>> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/table-sessions/${sessionId}/orders`, {
    method: 'POST',
    body: JSON.stringify({ lines }),
  });
  if (res.ok) {
    return { ok: true, data: (await res.json()) as TableSessionDetail };
  }
  return { ok: false, errors: await extractErrors(res) };
}

export async function updateOrderLine(
  storeId: number,
  lineId: number,
  body: { quantity: number; note: string }
): Promise<Result<OrderLine>> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/order-lines/${lineId}`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, data: (await res.json()) as OrderLine };
  }
  return { ok: false, errors: await extractErrors(res) };
}

export async function cancelOrderLine(
  storeId: number,
  lineId: number,
  body: CancelOrderLineRequest
): Promise<Result<OrderLine>> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/order-lines/${lineId}/cancel`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, data: (await res.json()) as OrderLine };
  }
  return { ok: false, errors: await extractErrors(res) };
}

export async function remakeOrderLine(
  storeId: number,
  lineId: number
): Promise<Result<OrderLine>> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/order-lines/${lineId}/remake`, {
    method: 'POST',
    body: JSON.stringify({}),
  });
  if (res.ok) {
    return { ok: true, data: (await res.json()) as OrderLine };
  }
  return { ok: false, errors: await extractErrors(res) };
}

export async function serveOrderLine(storeId: number, lineId: number): Promise<Result<OrderLine>> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/order-lines/${lineId}/serve`, {
    method: 'PATCH',
  });
  if (res.ok) {
    return { ok: true, data: (await res.json()) as OrderLine };
  }
  return { ok: false, errors: await extractErrors(res) };
}
