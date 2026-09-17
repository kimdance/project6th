import { getTenantApiBaseUrl } from '../config';

export interface ErrorItem {
  message: string;
  fields: string[];
}

export interface ApiErrorResponse {
  errors?: ErrorItem[];
}

export type ApiResult<T> = { ok: true; data: T } | { ok: false; errors: ErrorItem[] };

/**
 * Authorization ヘッダ付きの fetch。ok でも 401/403/404 等でも Response をそのまま返す。
 * 一覧の再取得が更新直後にブラウザキャッシュの古い内容を拾わないよう、キャッシュは常に無効化する。
 */
export function authedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const token = localStorage.getItem('accessToken');
  return fetch(`${getTenantApiBaseUrl()}${path}`, {
    ...init,
    cache: 'no-store',
    headers: {
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      Authorization: `Bearer ${token ?? ''}`,
      ...init.headers,
    },
  });
}

/**
 * ファイルアップロード用の fetch（FR-D01のメニュー写真等）。`Content-Type` を明示しないことで、
 * ブラウザが `multipart/form-data; boundary=...` を自動付与する（`authedFetch` は JSON 前提で
 * 固定してしまうため使えない）。
 */
export function authedUpload(path: string, formData: FormData): Promise<Response> {
  const token = localStorage.getItem('accessToken');
  return fetch(`${getTenantApiBaseUrl()}${path}`, {
    method: 'POST',
    body: formData,
    cache: 'no-store',
    headers: {
      Authorization: `Bearer ${token ?? ''}`,
    },
  });
}

/** エラーレスポンス（{ errors: ErrorItem[] }）から表示用のメッセージ一覧を取り出す。 */
export async function extractErrors(res: Response): Promise<ErrorItem[]> {
  const body: ApiErrorResponse = await res.json().catch(() => ({}));
  return body.errors && body.errors.length > 0
    ? body.errors
    : [{ message: '保存に失敗しました。', fields: [] }];
}
