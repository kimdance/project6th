import { authedFetch, extractErrors, type ErrorItem } from './http';

/** STANDARD_10（標準税率）／REDUCED_8（軽減税率）。区分のコード自体は税率変更後も変わらない。 */
export type TaxCategory = 'STANDARD_10' | 'REDUCED_8';

export interface TaxRate {
  id: number;
  taxCategory: TaxCategory;
  ratePercent: number;
  /** "YYYY-MM-DD" */
  effectiveFrom: string;
  /** 今日時点で実際に適用されている税率か。 */
  currentlyEffective: boolean;
}

export interface TaxRateRequest {
  taxCategory: TaxCategory;
  ratePercent: number;
  effectiveFrom: string;
}

type Result<T> = { ok: true; data: T } | { ok: false; errors: ErrorItem[] };

export async function fetchTaxRates(storeId: number): Promise<TaxRate[]> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/tax-rates`);
  if (!res.ok) {
    return [];
  }
  return (await res.json()) as TaxRate[];
}

export async function createTaxRate(storeId: number, body: TaxRateRequest): Promise<Result<TaxRate>> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/tax-rates`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, data: (await res.json()) as TaxRate };
  }
  return { ok: false, errors: await extractErrors(res) };
}

export async function deleteTaxRate(
  storeId: number,
  rateId: number
): Promise<{ ok: true } | { ok: false; errors: ErrorItem[] }> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/tax-rates/${rateId}`, {
    method: 'DELETE',
  });
  if (res.ok) {
    return { ok: true };
  }
  return { ok: false, errors: await extractErrors(res) };
}
