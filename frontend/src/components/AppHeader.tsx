import React, { useEffect, useState } from 'react';
import { getTenantApiBaseUrl } from '../config';

/**
 * 全画面共通のヘッダー。「{会社名} 店舗管理システム」と表示する。
 * 会社名はサブドメインから解決する GET /api/v1/auth/tenant から取得する
 * （ログイン前後どちらの画面でも、認証なしで呼べるエンドポイント。04_architecture.md §6.1）。
 */
export const AppHeader: React.FC = () => {
  const [companyName, setCompanyName] = useState<string | null>(null);

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

  return (
    <header
      style={{
        padding: '16px 24px',
        borderBottom: '1px solid #ddd',
        textAlign: 'left',
        backgroundColor: '#f8f9fa',
      }}
    >
      <h1 style={{ fontSize: '18px', margin: 0, fontWeight: 600 }}>
        {companyName ? `${companyName} 店舗管理システム` : '店舗管理システム'}
      </h1>
    </header>
  );
};
