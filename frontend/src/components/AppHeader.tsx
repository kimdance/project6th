import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { getTenantApiBaseUrl } from '../config';
import { fetchMe, ROLE_LABELS, type Me } from '../api/session';

/**
 * 全画面共通のヘッダー。「{会社名} 店舗管理システム」と、ログイン中であれば
 * 右側にユーザー情報（氏名・ロール・所属店舗）と「ホーム」「アカウント設定」「ログアウト」を表示する
 * （2026-09-16追補：各画面に個別にあった「ホームに戻る」「アカウント設定」「ログアウト」を
 * ここに一本化した）。
 * 会社名はサブドメインから解決する GET /api/v1/auth/tenant から取得する
 * （ログイン前後どちらの画面でも、認証なしで呼べるエンドポイント。04_architecture.md §6.1）。
 *
 * お客様向けの入口（`/`）では、ブラウザに古いスタッフの認証トークンが残っていても
 * スタッフの氏名・ロールや「ホーム」等のボタンを一切表示しない（2026-09-16追補・不具合修正）。
 * 同じ端末でスタッフ→お客様の順に画面を開いた場合でも内部情報が漏れないようにするため。
 *
 * このヘッダーはルーティングの外（画面遷移をまたいでマウントされたまま）に置かれているため、
 * ログイン・ログアウト直後にユーザー情報を更新するには、遷移のたびに /me を取得し直す必要がある
 * （location.pathname を依存配列に入れて画面遷移ごとに再取得する）。
 */
export const AppHeader: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const isPublicPage = location.pathname === '/';
  const [companyName, setCompanyName] = useState<string | null>(null);
  const [me, setMe] = useState<Me | null>(null);

  useEffect(() => {
    let cancelled = false;

    fetch(`${getTenantApiBaseUrl()}/api/v1/auth/tenant`)
      .then((res) => (res.ok ? res.json() : null))
      .then((data: { companyName: string } | null) => {
        if (!cancelled && data) {
          setCompanyName(data.companyName);
        }
      })
      .catch(() => {
        // 会社名が取れなくても致命的ではないため、既定のタイトルのままにする。
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (isPublicPage) {
      // お客様向けの入口ではスタッフの認証状態を一切扱わない（トークンが残っていても問い合わせない）。
      setMe(null);
      return;
    }
    let cancelled = false;

    fetchMe().then((result) => {
      if (!cancelled) {
        setMe(result);
      }
    });

    return () => {
      cancelled = true;
    };
    // ログイン・ログアウトは必ず画面遷移（navigate）を伴うため、パス変更のたびに見直す。
  }, [location.pathname, isPublicPage]);

  const handleLogout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    setMe(null);
    navigate('/staff', { replace: true });
  };

  return (
    <header
      style={{
        padding: '16px 24px',
        borderBottom: '1px solid #ddd',
        backgroundColor: '#f8f9fa',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '16px',
        flexWrap: 'wrap',
      }}
    >
      <h1 style={{ fontSize: '18px', margin: 0, fontWeight: 600, textAlign: 'left' }}>
        {location.pathname === '/'
          ? companyName
            ? `${companyName} ご予約`
            : 'ご予約'
          : companyName
            ? `${companyName} 店舗管理システム`
            : '店舗管理システム'}
      </h1>

      {me && (
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>
          <div style={{ fontSize: '13px', color: '#555', textAlign: 'right' }}>
            {me.name} さん（{ROLE_LABELS[me.role]}）
            {me.stores.length > 0 && `・${me.stores.map((s) => s.name).join('・')}`}
          </div>
          <div style={{ display: 'flex', gap: '8px' }}>
            {location.pathname !== '/home' && (
              <HeaderButton onClick={() => navigate('/home')}>ホーム</HeaderButton>
            )}
            <HeaderButton onClick={() => navigate('/account')}>アカウント設定</HeaderButton>
            <HeaderButton onClick={handleLogout}>ログアウト</HeaderButton>
          </div>
        </div>
      )}
    </header>
  );
};

const HeaderButton: React.FC<{ onClick: () => void; children: React.ReactNode }> = ({
  onClick,
  children,
}) => (
  <button
    type="button"
    onClick={onClick}
    style={{
      padding: '6px 12px',
      fontSize: '13px',
      backgroundColor: '#fff',
      color: '#333',
      border: '1px solid #ccc',
      borderRadius: '4px',
      cursor: 'pointer',
      whiteSpace: 'nowrap',
    }}
  >
    {children}
  </button>
);
