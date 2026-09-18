import { authedFetch, extractErrors, type ErrorItem } from './http';

export interface Store {
  id: number;
  name: string;
  address: string | null;
  phone: string | null;
  businessHours: string | null;
  seatCount: number;
  timezone: string;
  active: boolean;
}

export interface StoreCreateRequest {
  name: string;
  address: string;
  phone: string;
  businessHours: string;
  seatCount: number;
}

export interface StoreSettings {
  storeId: number;
  name: string;
  address: string | null;
  phone: string | null;
  businessHours: string | null;
  seatCount: number;
  /** FLOOR / CEIL / ROUND */
  taxRounding: string;
  priceIncludesTax: boolean;
  invoiceRegNo: string | null;
  /** APPROVAL（承認制） / INSTANT（即時確定） */
  webReservationMode: string;
  cancelChargeDefaultCustomer: boolean;
  cancelChargeDefaultStore: boolean;
  /** 提供後の注文明細の取消を店長承認必須にするか（FR-E03）。 */
  requireManagerApprovalForServeCancel: boolean;
  /** 会計の取消・返金・値引きを店長承認必須にするか（FR-G10）。 */
  requireManagerApprovalForVoidRefund: boolean;
}

/** PUT の送信ボディ。フォームで扱いやすいよう、null は使わず空文字を許容する。 */
export interface StoreSettingsRequest {
  name: string;
  address: string;
  phone: string;
  businessHours: string;
  seatCount: number;
  /** FLOOR / CEIL / ROUND */
  taxRounding: string;
  priceIncludesTax: boolean;
  invoiceRegNo: string;
  /** APPROVAL（承認制） / INSTANT（即時確定） */
  webReservationMode: string;
  cancelChargeDefaultCustomer: boolean;
  cancelChargeDefaultStore: boolean;
  requireManagerApprovalForServeCancel: boolean;
  requireManagerApprovalForVoidRefund: boolean;
}

/** 自テナントの店舗一覧。0件（未作成）または複数件。 */
export async function fetchStores(): Promise<Store[]> {
  const res = await authedFetch('/api/v1/stores');
  if (!res.ok) {
    return [];
  }
  return (await res.json()) as Store[];
}

export async function createStore(
  body: StoreCreateRequest
): Promise<{ ok: true; store: Store } | { ok: false; errors: ErrorItem[] }> {
  const res = await authedFetch('/api/v1/stores', { method: 'POST', body: JSON.stringify(body) });
  if (res.ok) {
    return { ok: true, store: (await res.json()) as Store };
  }
  return { ok: false, errors: await extractErrors(res) };
}

export async function fetchStoreSettings(storeId: number): Promise<StoreSettings | null> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/settings`);
  if (!res.ok) {
    return null;
  }
  return (await res.json()) as StoreSettings;
}

export async function updateStoreSettings(
  storeId: number,
  body: StoreSettingsRequest
): Promise<{ ok: true; settings: StoreSettings } | { ok: false; errors: ErrorItem[] }> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/settings`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, settings: (await res.json()) as StoreSettings };
  }
  return { ok: false, errors: await extractErrors(res) };
}
