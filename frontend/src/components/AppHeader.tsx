import React, { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { getTenantApiBaseUrl } from '../config';
import { fetchMe, ROLE_LABELS, type Me } from '../api/session';

/**
 * 全画面共通のヘッダー。「{会社名} 店舗管理システム」と、ログイン中であれば
 * 右側にユーザー情報（氏名・ロール・所属店舗）も表示する。
 * 会社名はサブドメインから解決する GET /api/v1/auth/tenant から取得する
 * （ログイン前後どちらの画面でも、認証なしで呼べるエンドポイント。04_architecture.md §6.1）。
 *
 * このヘッダーはルーティングの外（画面遷移をまたいでマウントされたまま）に置かれているため、
 * ログイン・ログアウト直後にユーザー情報を更新するには、遷移のたびに /me を取得し直す必要がある
 * （location.pathname を依存配列に入れて画面遷移ごとに再取得する）。
 */
export const AppHeader: React.FC = () => {
  const location = useLocation();
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
  }, [location.pathname]);

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
        {companyName ? `${companyName} 店舗管理システム` : '店舗管理システム'}
      </h1>

      {me && (
        <div style={{ fontSize: '13px', color: '#555', textAlign: 'right' }}>
          {me.name} さん（{ROLE_LABELS[me.role]}）
          {me.stores.length > 0 && `・${me.stores.map((s) => s.name).join('・')}`}
        </div>
      )}
    </header>
  );
};
