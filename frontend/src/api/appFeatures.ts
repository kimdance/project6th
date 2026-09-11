import { getTenantApiBaseUrl } from '../config';

/** GET /api/v1/app-features の1件。ログイン中ユーザーのロールで表示可否が絞り込み済み。 */
export interface AppFeature {
  key: string;
  title: string;
  description: string;
  path: string;
}

/**
 * ホーム画面に並べる機能の入口を取得する。表示可否（ロールごとの出し分け）はDB
 * （`app_feature`／`app_feature_role`）で管理しており、サーバ側で絞り込み済みの結果が返る。
 * トークンが無い・失効している場合は空配列を返す（呼び出し側で /me の結果を優先して判断する）。
 */
export async function fetchAppFeatures(): Promise<AppFeature[]> {
  const token = localStorage.getItem('accessToken');
  if (!token) {
    return [];
  }

  try {
    const res = await fetch(`${getTenantApiBaseUrl()}/api/v1/app-features`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) {
      return [];
    }
    return (await res.json()) as AppFeature[];
  } catch {
    return [];
  }
}
