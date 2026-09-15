import { getTenantApiBaseUrl } from '../config';
import type { ErrorItem } from './http';

/** GET /api/v1/public/stores のレスポンス。 */
export interface PublicStore {
  id: number;
  name: string;
}

/** POST /api/v1/public/stores/{storeId}/reservations の送信ボディ（FR-C03）。 */
export interface PublicReservationRequest {
  reservedAt: string;
  partySize: number;
  guestName: string;
  guestPhone: string;
  guestEmail: string;
  requestNote: string;
}

/** POST /api/v1/public/stores/{storeId}/reservations のレスポンス。 */
export interface PublicReservationResult {
  id: number;
  reservedAt: string;
  partySize: number;
  guestName: string;
  /** REQUESTED（承認待ち）／CONFIRMED（確定） */
  status: 'REQUESTED' | 'CONFIRMED';
}

type Result<T> = { ok: true; data: T } | { ok: false; errors: ErrorItem[] };

/** 認証不要（お客様向け）。会社はサブドメインから解決される。 */
export async function fetchPublicStores(): Promise<PublicStore[]> {
  const res = await fetch(`${getTenantApiBaseUrl()}/api/v1/public/stores`, { cache: 'no-store' });
  if (!res.ok) {
    return [];
  }
  return (await res.json()) as PublicStore[];
}

export async function createPublicReservation(
  storeId: number,
  body: PublicReservationRequest
): Promise<Result<PublicReservationResult>> {
  const res = await fetch(`${getTenantApiBaseUrl()}/api/v1/public/stores/${storeId}/reservations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, data: (await res.json()) as PublicReservationResult };
  }
  const errorBody: { errors?: ErrorItem[] } = await res.json().catch(() => ({}));
  return {
    ok: false,
    errors:
      errorBody.errors && errorBody.errors.length > 0
        ? errorBody.errors
        : [{ message: '予約の送信に失敗しました。', fields: [] }],
  };
}
