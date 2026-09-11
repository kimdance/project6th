export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

/**
 * テナント（<company_code>.localhost）に対応するバックエンドのアドレス。
 * 04_architecture.md §6.1：テナントはURLのサブドメインで識別するため、フロントも
 * 自分が開かれているサブドメインに合わせてバックエンドを呼ぶ（開発時はポートのみ違う）。
 * VITE_API_BASE_URL が明示されていればそちらを優先する（本番はフロントと別ホストになりうるため）。
 */
export function getTenantApiBaseUrl(): string {
  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL;
  }
  return `http://${window.location.hostname}:8080`;
}
