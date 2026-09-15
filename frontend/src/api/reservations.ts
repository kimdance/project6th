import { authedFetch, extractErrors, type ErrorItem } from './http';

/** WEB／PHONE／WALK_IN */
export type ReservationChannel = 'WEB' | 'PHONE' | 'WALK_IN';

/** REQUESTED／CONFIRMED／SEATED／DONE／CANCELLED／NO_SHOW（03_domain_model.md §4.1） */
export type ReservationStatus = 'REQUESTED' | 'CONFIRMED' | 'SEATED' | 'DONE' | 'CANCELLED' | 'NO_SHOW';

/** GET /api/v1/reservations・POST/PATCH /api/v1/stores/{storeId}/reservations[/{id}] のレスポンス。 */
export interface Reservation {
  id: number;
  storeId: number;
  storeName: string;
  reservedAt: string;
  partySize: number;
  guestName: string;
  guestPhone: string | null;
  guestEmail: string | null;
  requestNote: string | null;
  channel: ReservationChannel;
  status: ReservationStatus;
  confirmedBy: string | null;
  confirmedAt: string | null;
  cancelledReason: string | null;
}

/** POST/PATCH の送信ボディ（詳細項目）。channelは新規登録時のみ参照される。 */
export interface ReservationRequest {
  reservedAt: string;
  partySize: number;
  guestName: string;
  guestPhone: string;
  guestEmail: string;
  requestNote: string;
  channel: 'PHONE' | 'WALK_IN';
}

type Result<T> = { ok: true; data: T } | { ok: false; errors: ErrorItem[] };

/**
 * 指定日から days 日分（既定1日＝日表示、7で週表示）を取得する。
 * storeId を省略すると、経営管理者は全店、店長・ホールは自分の所属店舗を横断して返す。
 */
export async function fetchReservations(
  storeId: number | 'ALL',
  date: string,
  days = 1
): Promise<Reservation[]> {
  const storeParam = storeId === 'ALL' ? '' : `&storeId=${storeId}`;
  const res = await authedFetch(`/api/v1/reservations?date=${date}&days=${days}${storeParam}`);
  if (!res.ok) {
    return [];
  }
  return (await res.json()) as Reservation[];
}

export async function createReservation(
  storeId: number,
  body: ReservationRequest
): Promise<Result<Reservation>> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/reservations`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, data: (await res.json()) as Reservation };
  }
  return { ok: false, errors: await extractErrors(res) };
}

export async function updateReservation(
  storeId: number,
  reservationId: number,
  body: ReservationRequest
): Promise<Result<Reservation>> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/reservations/${reservationId}`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, data: (await res.json()) as Reservation };
  }
  return { ok: false, errors: await extractErrors(res) };
}

export async function updateReservationStatus(
  storeId: number,
  reservationId: number,
  status: ReservationStatus,
  cancelledReason?: string
): Promise<Result<Reservation>> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/reservations/${reservationId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status, cancelledReason }),
  });
  if (res.ok) {
    return { ok: true, data: (await res.json()) as Reservation };
  }
  return { ok: false, errors: await extractErrors(res) };
}
