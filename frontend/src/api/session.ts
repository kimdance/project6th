import { getTenantApiBaseUrl } from '../config';
import { authedFetch, extractErrors, type ErrorItem } from './http';

/** ログイン中のユーザーのロール（02_requirements.md §3.1）。 */
export type Role = 'OWNER' | 'MANAGER' | 'HALL' | 'KITCHEN' | 'PARTTIME';

/** 店舗の簡易参照（id・店名のみ）。 */
export interface StoreRef {
  id: number;
  name: string;
}

/** GET /api/v1/auth/me のレスポンス。 */
export interface Me {
  userId: number;
  name: string;
  email: string;
  telnumber: string | null;
  role: Role;
  companyName: string;
  /** 空配列 = 全店（本部ユーザー等）。1人が複数店舗を兼任できる。 */
  stores: StoreRef[];
}

/** PUT /api/v1/auth/me の送信ボディ。ロール・所属店舗はここでは変更できない。 */
export interface ProfileUpdateRequest {
  name: string;
  email: string;
  telnumber: string;
}

/**
 * ロールの日本語表示名。
 * OWNER の表示名は「経営管理者」（2026-09-11改訂。実運用では「オーナー」と「役員」が別の
 * 立場であることが多いため、権限の実態に合わせた呼び方にした。ロールコード自体は OWNER のまま）。
 */
export const ROLE_LABELS: Record<Role, string> = {
  OWNER: '経営管理者',
  MANAGER: '店長',
  HALL: 'ホールスタッフ',
  KITCHEN: 'キッチンスタッフ',
  PARTTIME: 'アルバイト',
};

/**
 * ログイン中のユーザー情報を取得する。
 * トークンが無い・失効している（401）場合は null を返すので、呼び出し側でログイン画面へ戻す。
 */
export async function fetchMe(): Promise<Me | null> {
  const token = localStorage.getItem('accessToken');
  if (!token) {
    return null;
  }

  try {
    const res = await fetch(`${getTenantApiBaseUrl()}/api/v1/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) {
      return null;
    }
    return (await res.json()) as Me;
  } catch {
    return null;
  }
}

/** 自分の氏名・メールアドレス・電話番号を変更する（PUT /api/v1/auth/me）。 */
export async function updateProfile(
  body: ProfileUpdateRequest
): Promise<{ ok: true; me: Me } | { ok: false; errors: ErrorItem[] }> {
  const res = await authedFetch('/api/v1/auth/me', { method: 'PUT', body: JSON.stringify(body) });
  if (res.ok) {
    return { ok: true, me: (await res.json()) as Me };
  }
  return { ok: false, errors: await extractErrors(res) };
}
