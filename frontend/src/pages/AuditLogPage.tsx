import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TopMessage } from '../components/TopMessage';
import { fetchMe, type Me, type StoreRef } from '../api/session';
import { fetchStores } from '../api/stores';
import {
  fetchAuditLogs,
  auditActionLabel,
  AUDIT_ACTION_LABELS,
  type AuditLogEntry,
} from '../api/auditLogs';

const PAGE_SIZE = 50;

/**
 * 監査ログの検索・閲覧画面（FR-J04）。経営管理者（全店）・店長（自分の所属店舗のみ）が
 * 利用できる（GET /api/v1/audit-logs はサーバ側でも同じ制限をかけている。店長は
 * ログイン履歴・ユーザー権限変更等の店舗に紐づかない全社共通の操作は見えない）。
 */
export const AuditLogPage: React.FC = () => {
  const navigate = useNavigate();

  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);
  const [stores, setStores] = useState<StoreRef[]>([]);
  const [forbidden, setForbidden] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const [storeId, setStoreId] = useState<string>('');
  const [action, setAction] = useState<string>('');
  const [actor, setActor] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  const [entries, setEntries] = useState<AuditLogEntry[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [searching, setSearching] = useState(false);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const search = async (targetPage: number) => {
    setSearching(true);
    setErrorMessage('');
    const result = await fetchAuditLogs({
      storeId: storeId ? Number(storeId) : undefined,
      action: action || undefined,
      actor: actor.trim() || undefined,
      from: from || undefined,
      to: to || undefined,
      page: targetPage,
      size: PAGE_SIZE,
    });
    setSearching(false);

    if (!result.ok) {
      if (result.status === 403) {
        setForbidden(true);
      } else {
        setErrorMessage('監査ログの取得に失敗しました。');
      }
      return;
    }
    setEntries(result.data.content);
    setTotalElements(result.data.totalElements);
    setPage(result.data.page);
    setExpandedId(null);
  };

  useEffect(() => {
    let cancelled = false;

    (async () => {
      const meResult = await fetchMe();
      if (cancelled) {
        return;
      }
      if (!meResult) {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        navigate('/staff', { replace: true });
        return;
      }
      setMe(meResult);

      if (meResult.role === 'OWNER' || meResult.role === 'MANAGER') {
        // オーナーは全店から選べるが、店長は自分の所属店舗しか見られないため
        // 選択肢も自分の所属店舗のみに絞る（フィルタで見えない店舗を選べても意味がないため）。
        const storeList = meResult.role === 'OWNER' ? await fetchStores() : meResult.stores;
        if (cancelled) {
          return;
        }
        setStores(storeList);
        await search(0);
      }
      if (!cancelled) {
        setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [navigate]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    search(0);
  };

  const totalPages = Math.max(1, Math.ceil(totalElements / PAGE_SIZE));

  if (loading || !me) {
    return (
      <div style={{ maxWidth: '960px', margin: '40px auto', padding: '20px' }}>
        <p>読み込み中...</p>
      </div>
    );
  }

  const isManager = me.role === 'MANAGER';

  if ((me.role !== 'OWNER' && !isManager) || forbidden) {
    return (
      <div style={{ maxWidth: '960px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
        <h2>監査ログ</h2>
        <p style={{ color: '#666' }}>この画面は経営管理者・店長のみご利用いただけます。</p>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '960px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
      <h2>監査ログ</h2>
      <p style={{ color: '#666', fontSize: '13px' }}>
        {isManager ? (
          <>
            自分の所属店舗に関する店舗設定・決済手段設定の変更などの重要操作を、期間・操作種別・
            実行者で検索できます（ログイン履歴やユーザー権限変更など店舗に紐づかない操作、他店舗の
            操作は表示されません）。
          </>
        ) : (
          <>
            ログイン・パスワード変更・ユーザー権限変更・店舗設定/決済手段設定の変更などの重要操作を、
            期間・店舗・操作種別・実行者で検索できます（FR-J04）。
          </>
        )}
      </p>

      <TopMessage messages={errorMessage} isError />

      <form
        onSubmit={handleSearchSubmit}
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
          gap: '12px',
          padding: '12px',
          border: '1px solid #ddd',
          borderRadius: '8px',
          marginBottom: '16px',
        }}
      >
        <FilterField label="店舗">
          <select value={storeId} onChange={(e) => setStoreId(e.target.value)} style={inputStyle}>
            <option value="">すべての店舗</option>
            {stores.map((store) => (
              <option key={store.id} value={store.id}>
                {store.name}
              </option>
            ))}
          </select>
        </FilterField>

        <FilterField label="操作種別">
          <select value={action} onChange={(e) => setAction(e.target.value)} style={inputStyle}>
            <option value="">すべて</option>
            {Object.entries(AUDIT_ACTION_LABELS).map(([code, label]) => (
              <option key={code} value={code}>
                {label}
              </option>
            ))}
          </select>
        </FilterField>

        <FilterField label="実行者（メールアドレス完全一致）">
          <input
            type="text"
            value={actor}
            onChange={(e) => setActor(e.target.value)}
            placeholder="例: owner@example.com"
            style={inputStyle}
          />
        </FilterField>

        <FilterField label="期間（から）">
          <input
            type="datetime-local"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            style={inputStyle}
          />
        </FilterField>

        <FilterField label="期間（まで）">
          <input
            type="datetime-local"
            value={to}
            onChange={(e) => setTo(e.target.value)}
            style={inputStyle}
          />
        </FilterField>

        <div style={{ display: 'flex', alignItems: 'flex-end' }}>
          <button type="submit" disabled={searching} style={searchButtonStyle}>
            {searching ? '検索中...' : '検索する'}
          </button>
        </div>
      </form>

      <p style={{ color: '#666', fontSize: '13px' }}>
        {totalElements}件中 {entries.length === 0 ? 0 : page * PAGE_SIZE + 1}〜
        {page * PAGE_SIZE + entries.length}件を表示
      </p>

      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
          <thead>
            <tr style={{ borderBottom: '2px solid #ddd', textAlign: 'left' }}>
              <th style={thStyle}>日時</th>
              <th style={thStyle}>実行者</th>
              <th style={thStyle}>店舗</th>
              <th style={thStyle}>操作</th>
              <th style={thStyle}>対象</th>
              <th style={thStyle}>IP / 端末</th>
              <th style={thStyle}></th>
            </tr>
          </thead>
          <tbody>
            {entries.length === 0 && (
              <tr>
                <td colSpan={7} style={{ padding: '16px', color: '#666', textAlign: 'center' }}>
                  該当するログがありません。
                </td>
              </tr>
            )}
            {entries.map((entry) => (
              <React.Fragment key={entry.id}>
                <tr style={{ borderBottom: '1px solid #eee' }}>
                  <td style={tdStyle}>{formatDateTime(entry.occurredAt)}</td>
                  <td style={tdStyle}>{entry.actor}</td>
                  <td style={tdStyle}>{entry.storeName ?? '（店舗なし）'}</td>
                  <td style={tdStyle}>{auditActionLabel(entry.action)}</td>
                  <td style={tdStyle}>
                    {entry.targetType}
                    {entry.targetId != null ? ` #${entry.targetId}` : ''}
                  </td>
                  <td style={tdStyle}>
                    {entry.ip ?? '-'}
                    <br />
                    <span style={{ color: '#999' }}>{entry.device ?? ''}</span>
                  </td>
                  <td style={tdStyle}>
                    {(entry.beforeSummary || entry.afterSummary) && (
                      <button
                        type="button"
                        onClick={() => setExpandedId(expandedId === entry.id ? null : entry.id)}
                        style={detailButtonStyle}
                      >
                        {expandedId === entry.id ? '閉じる' : '詳細'}
                      </button>
                    )}
                  </td>
                </tr>
                {expandedId === entry.id && (
                  <tr style={{ borderBottom: '1px solid #eee', background: '#f8f9fa' }}>
                    <td colSpan={7} style={{ padding: '10px 8px', fontSize: '12px' }}>
                      {entry.beforeSummary && (
                        <div style={{ marginBottom: '6px' }}>
                          <strong>変更前:</strong> {entry.beforeSummary}
                        </div>
                      )}
                      {entry.afterSummary && (
                        <div>
                          <strong>変更後:</strong> {entry.afterSummary}
                        </div>
                      )}
                    </td>
                  </tr>
                )}
              </React.Fragment>
            ))}
          </tbody>
        </table>
      </div>

      <div style={{ display: 'flex', justifyContent: 'center', gap: '12px', margin: '16px 0' }}>
        <button
          type="button"
          onClick={() => search(page - 1)}
          disabled={page <= 0 || searching}
          style={pageButtonStyle}
        >
          ← 前へ
        </button>
        <span style={{ padding: '8px 0' }}>
          {page + 1} / {totalPages}
        </span>
        <button
          type="button"
          onClick={() => search(page + 1)}
          disabled={page + 1 >= totalPages || searching}
          style={pageButtonStyle}
        >
          次へ →
        </button>
      </div>
    </div>
  );
};

function formatDateTime(isoLike: string): string {
  const d = new Date(isoLike);
  if (Number.isNaN(d.getTime())) {
    return isoLike;
  }
  return d.toLocaleString('ja-JP');
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '8px',
  boxSizing: 'border-box',
  border: '1px solid #ccc',
  borderRadius: '4px',
};

const thStyle: React.CSSProperties = {
  padding: '8px',
  whiteSpace: 'nowrap',
};

const tdStyle: React.CSSProperties = {
  padding: '8px',
  verticalAlign: 'top',
};

const searchButtonStyle: React.CSSProperties = {
  width: '100%',
  padding: '9px',
  backgroundColor: '#007bff',
  color: '#fff',
  border: 'none',
  borderRadius: '4px',
  cursor: 'pointer',
};

const detailButtonStyle: React.CSSProperties = {
  padding: '4px 10px',
  fontSize: '12px',
  backgroundColor: '#fff',
  color: '#333',
  border: '1px solid #ccc',
  borderRadius: '4px',
  cursor: 'pointer',
  whiteSpace: 'nowrap',
};

const pageButtonStyle: React.CSSProperties = {
  padding: '8px 16px',
  backgroundColor: '#fff',
  color: '#333',
  border: '1px solid #ccc',
  borderRadius: '4px',
  cursor: 'pointer',
};

const FilterField: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div>
    <label style={{ display: 'block', marginBottom: '4px', fontSize: '13px', color: '#555' }}>
      {label}
    </label>
    {children}
  </div>
);
