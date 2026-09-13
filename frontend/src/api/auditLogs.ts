import { authedFetch } from './http';

/** audit_log.action の代表値（AuditActions）。今後の機能追加で増える想定。 */
export type AuditAction =
  | 'LOGIN_SUCCESS'
  | 'LOGIN_FAILURE'
  | 'PASSWORD_CHANGE'
  | 'USER_REGISTER'
  | 'PERMISSION_CHANGE'
  | 'STORE_SETTING_CHANGE'
  | 'PAYMENT_SETTING_CHANGE';

/** 操作種別の日本語表示名（AuditActions と対応。未知の値はそのままのコードを表示する）。 */
export const AUDIT_ACTION_LABELS: Record<AuditAction, string> = {
  LOGIN_SUCCESS: 'ログイン成功',
  LOGIN_FAILURE: 'ログイン失敗',
  PASSWORD_CHANGE: 'パスワード変更',
  USER_REGISTER: 'ユーザー登録',
  PERMISSION_CHANGE: 'ユーザー権限変更',
  STORE_SETTING_CHANGE: '店舗設定変更',
  PAYMENT_SETTING_CHANGE: '決済手段設定変更',
};

export function auditActionLabel(action: string): string {
  return AUDIT_ACTION_LABELS[action as AuditAction] ?? action;
}

/** GET /api/v1/audit-logs の1件。 */
export interface AuditLogEntry {
  id: number;
  storeId: number | null;
  storeName: string | null;
  actor: string;
  action: string;
  targetType: string;
  targetId: number | null;
  beforeSummary: string | null;
  afterSummary: string | null;
  ip: string | null;
  device: string | null;
  occurredAt: string;
}

export interface AuditLogPage {
  content: AuditLogEntry[];
  totalElements: number;
  page: number;
  size: number;
}

export interface AuditLogSearchParams {
  storeId?: number;
  action?: string;
  actor?: string;
  /** datetime-local の値（例 "2026-09-13T10:00"）をそのまま渡す。 */
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

/**
 * 監査ログの検索（FR-J04）。経営管理者以外は403になるため、呼び出し側で
 * ステータスを見て案内メッセージを出し分ける。
 */
export async function fetchAuditLogs(
  params: AuditLogSearchParams
): Promise<{ ok: true; data: AuditLogPage } | { ok: false; status: number }> {
  const query = new URLSearchParams();
  if (params.storeId != null) query.set('storeId', String(params.storeId));
  if (params.action) query.set('action', params.action);
  if (params.actor) query.set('actor', params.actor);
  if (params.from) query.set('from', params.from);
  if (params.to) query.set('to', params.to);
  query.set('page', String(params.page ?? 0));
  query.set('size', String(params.size ?? 50));

  const res = await authedFetch(`/api/v1/audit-logs?${query.toString()}`);
  if (!res.ok) {
    return { ok: false, status: res.status };
  }
  return { ok: true, data: (await res.json()) as AuditLogPage };
}
