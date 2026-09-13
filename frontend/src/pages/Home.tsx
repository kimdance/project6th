import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchMe, type Me } from '../api/session';
import { fetchAppFeatures, type AppFeature } from '../api/appFeatures';

/**
 * ログイン後の共通トップ画面（04_architecture.md §6）。
 * GET /api/v1/auth/me でログイン中のユーザーを取得して「ようこそ ◯◯さん」を表示し、
 * GET /api/v1/app-features で、そのユーザーのロールに応じた機能の入口（DBの app_feature／
 * app_feature_role で管理。サーバ側で絞り込み済み）を取得して並べる。
 * トークンが失効していれば（/me が 401）ログイン画面へ戻す。
 */
export const Home = () => {
  const navigate = useNavigate();
  const [me, setMe] = useState<Me | null>(null);
  const [features, setFeatures] = useState<AppFeature[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    fetchMe().then(async (result) => {
      if (cancelled) {
        return;
      }
      if (!result) {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        navigate('/', { replace: true });
        return;
      }
      setMe(result);
      const visibleFeatures = await fetchAppFeatures();
      if (!cancelled) {
        setFeatures(visibleFeatures);
        setLoading(false);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [navigate]);

  const handleLogout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    navigate('/', { replace: true });
  };

  if (loading || !me) {
    return (
      <div style={{ maxWidth: '720px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
        <p>読み込み中...</p>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '720px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
      <h2>ホーム</h2>

      {features.length > 0 ? (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
            gap: '16px',
            marginTop: '24px',
          }}
        >
          {features.map((feature) => (
            <button
              key={feature.key}
              type="button"
              onClick={() => navigate(feature.path)}
              style={{
                textAlign: 'left',
                padding: '16px',
                border: '1px solid #ddd',
                borderRadius: '8px',
                background: '#fff',
                cursor: 'pointer',
              }}
            >
              <div style={{ fontWeight: 600, marginBottom: '6px' }}>{feature.title}</div>
              <div style={{ fontSize: '13px', color: '#666' }}>{feature.description}</div>
            </button>
          ))}
        </div>
      ) : (
        <p style={{ color: '#666', marginTop: '24px' }}>現在ご利用いただける機能はありません。</p>
      )}

      <div style={{ marginTop: '32px', display: 'flex', gap: '12px' }}>
        <button
          type="button"
          onClick={() => navigate('/account')}
          style={{
            padding: '10px 20px',
            backgroundColor: '#fff',
            color: '#333',
            border: '1px solid #ccc',
            borderRadius: '4px',
            cursor: 'pointer',
          }}
        >
          アカウント設定
        </button>
        <button
          type="button"
          onClick={handleLogout}
          style={{
            padding: '10px 20px',
            backgroundColor: '#6c757d',
            color: '#fff',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
          }}
        >
          ログアウト
        </button>
      </div>
    </div>
  );
};
