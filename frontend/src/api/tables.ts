import { authedFetch, extractErrors, type ErrorItem } from './http';

/** 卓の席種類。COUNTER は1席ずつ卓番号を分けて登録する運用のため、常に seatCount=1 になる。 */
export type SeatType = 'COUNTER' | 'TABLE';

/** 卓（テーブル）。GET/POST/PUT /api/v1/stores/{storeId}/tables[/{tableId}] のレスポンス。 */
export interface DiningTable {
  id: number;
  tableNo: string;
  seatCount: number;
  seatType: SeatType;
  area: string | null;
  /** モバイルオーダー用QRの識別子。サーバが自動発番する（編集不可）。 */
  qrToken: string;
  /** EMPTY / OCCUPIED / BILLING。実際の卓の利用状況（このマスタ画面では変更しない）。 */
  status: string;
  active: boolean;
}

/** POST/PUT の送信ボディ。 */
export interface TableRequest {
  tableNo: string;
  seatCount: number;
  seatType: SeatType;
  area: string;
  active: boolean;
}

export async function fetchTables(storeId: number): Promise<DiningTable[]> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/tables`);
  if (!res.ok) {
    return [];
  }
  return (await res.json()) as DiningTable[];
}

export async function createTable(
  storeId: number,
  body: TableRequest
): Promise<{ ok: true; table: DiningTable } | { ok: false; errors: ErrorItem[] }> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/tables`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, table: (await res.json()) as DiningTable };
  }
  return { ok: false, errors: await extractErrors(res) };
}

export async function updateTable(
  storeId: number,
  tableId: number,
  body: TableRequest
): Promise<{ ok: true; table: DiningTable } | { ok: false; errors: ErrorItem[] }> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/tables/${tableId}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, table: (await res.json()) as DiningTable };
  }
  return { ok: false, errors: await extractErrors(res) };
}
