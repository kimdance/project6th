import { authedFetch, extractErrors, type ErrorItem } from './http';
import type { Role, StoreRef } from './session';

/** ACTIVE（在籍中）／LOCKED（連続ログイン失敗による一時ロック、自動解除）／RETIRED（退職済み）。 */
export type UserStatus = 'ACTIVE' | 'LOCKED' | 'RETIRED';

/** GET /api/v1/users・PUT /api/v1/users/{userId} の1件。 */
export interface UserSummary {
  id: number;
  name: string;
  email: string;
  role: Role;
  status: UserStatus;
  /** 空配列 = 全店（未設定）。1人が複数店舗を兼任できる。 */
  stores: StoreRef[];
}

export interface UserUpdateRequest {
  role: Role;
  /** 空配列 = 全店（未設定）に戻す。1人が複数店舗を兼任できる。 */
  storeIds: number[];
  /** ACTIVE（在籍中）／RETIRED（退職済み）のみ指定可。LOCKEDはここでは指定できない。 */
  status: 'ACTIVE' | 'RETIRED';
}

/** 自テナントのユーザー一覧。経営管理者のみ取得できる。 */
export async function fetchUsers(): Promise<UserSummary[]> {
  const res = await authedFetch('/api/v1/users');
  if (!res.ok) {
    return [];
  }
  return (await res.json()) as UserSummary[];
}

export async function updateUser(
  userId: number,
  body: UserUpdateRequest
): Promise<{ ok: true; user: UserSummary } | { ok: false; errors: ErrorItem[] }> {
  const res = await authedFetch(`/api/v1/users/${userId}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, user: (await res.json()) as UserSummary };
  }
  return { ok: false, errors: await extractErrors(res) };
}
