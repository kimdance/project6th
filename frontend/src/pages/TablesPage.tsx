import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TopMessage } from '../components/TopMessage';
import { fetchMe, type Me } from '../api/session';
import { fetchStores, type Store } from '../api/stores';
import {
  fetchTables,
  createTable,
  updateTable,
  type DiningTable,
  type TableRequest,
  type SeatType,
} from '../api/tables';

const STATUS_LABELS: Record<string, string> = {
  EMPTY: '空席',
  OCCUPIED: '利用中',
  BILLING: '会計中',
};

const SEAT_TYPE_LABELS: Record<SeatType, string> = {
  COUNTER: 'カウンター',
  TABLE: 'テーブル',
};

const EMPTY_FORM: TableRequest = { tableNo: '', seatCount: 2, seatType: 'TABLE', area: '', active: true };

type View = 'select-store' | 'list' | 'form';
type SeatTypeFilter = 'ALL' | SeatType;
type ActiveFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';

/**
 * 卓（テーブル）マスタの管理画面（FR-B02）。
 * 店舗が複数あれば先に店舗を選ばせ、選んだ店舗の卓を一覧・作成・編集する。
 * 編集できるのは経営管理者（全店）／店長（自店のみ）。閲覧は誰でもできる（TableService）。
 */
export const TablesPage: React.FC = () => {
  const navigate = useNavigate();

  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);
  const [stores, setStores] = useState<Store[]>([]);
  const [selectedStoreId, setSelectedStoreId] = useState<number | null>(null);
  const [tables, setTables] = useState<DiningTable[]>([]);
  const [seatTypeFilter, setSeatTypeFilter] = useState<SeatTypeFilter>('ALL');
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>('ALL');
  const [view, setView] = useState<View>('list');

  const [editingTableId, setEditingTableId] = useState<number | null>(null);
  const [form, setForm] = useState<TableRequest>(EMPTY_FORM);

  const [messages, setMessages] = useState<string[]>([]);
  const [errorFields, setErrorFields] = useState<string[]>([]);
  const [successMessage, setSuccessMessage] = useState('');
  const [saving, setSaving] = useState(false);

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
        navigate('/', { replace: true });
        return;
      }
      setMe(meResult);

      const storeList = await fetchStores();
      if (cancelled) {
        return;
      }
      setStores(storeList);

      if (storeList.length === 1) {
        const storeId = storeList[0].id;
        setSelectedStoreId(storeId);
        setTables(await fetchTables(storeId));
        setView('list');
      } else {
        setView('select-store');
      }
      setLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [navigate]);

  const canEditStore = (storeId: number) =>
    !!me && (me.role === 'OWNER' || (me.role === 'MANAGER' && me.storeId === storeId));

  const selectStore = async (storeId: number) => {
    setSelectedStoreId(storeId);
    setTables(await fetchTables(storeId));
    setSeatTypeFilter('ALL');
    setActiveFilter('ALL');
    setView('list');
  };

  const visibleTables = tables.filter((table) => {
    if (seatTypeFilter !== 'ALL' && table.seatType !== seatTypeFilter) {
      return false;
    }
    if (activeFilter === 'ACTIVE' && !table.active) {
      return false;
    }
    if (activeFilter === 'INACTIVE' && table.active) {
      return false;
    }
    return true;
  });

  const resetMessages = () => {
    setMessages([]);
    setErrorFields([]);
    setSuccessMessage('');
  };

  const openCreate = () => {
    resetMessages();
    setEditingTableId(null);
    setForm(EMPTY_FORM);
    setView('form');
  };

  const openEdit = (table: DiningTable) => {
    resetMessages();
    setEditingTableId(table.id);
    setForm({
      tableNo: table.tableNo,
      seatCount: table.seatCount,
      seatType: table.seatType,
      area: table.area ?? '',
      active: table.active,
    });
    setView('form');
  };

  /** カウンター席は1席ずつ卓を分けて登録する運用のため、選ぶと席数を自動で1にする。 */
  const handleSeatTypeChange = (seatType: SeatType) => {
    setForm((prev) => ({ ...prev, seatType, seatCount: seatType === 'COUNTER' ? 1 : prev.seatCount }));
    clearFieldError('seatType');
  };

  const backToList = () => {
    resetMessages();
    setView('list');
  };

  const clearFieldError = (field: string) => {
    if (errorFields.includes(field)) {
      setErrorFields((prev) => prev.filter((f) => f !== field));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedStoreId === null) {
      return;
    }
    resetMessages();
    setSaving(true);

    try {
      const result =
        editingTableId === null
          ? await createTable(selectedStoreId, form)
          : await updateTable(selectedStoreId, editingTableId, form);

      if (result.ok) {
        setTables(await fetchTables(selectedStoreId));
        setView('list');
        setSuccessMessage(`「${result.table.tableNo}」を保存しました。`);
        return;
      }
      setMessages(result.errors.map((err) => err.message));
      setErrorFields(Array.from(new Set(result.errors.flatMap((err) => err.fields))));
    } catch (error) {
      console.error('通信エラー:', error);
      setMessages(['サーバーとの通信に失敗しました。']);
    } finally {
      setSaving(false);
    }
  };

  const getInputStyle = (field: string) => ({
    width: '100%',
    padding: '8px',
    marginTop: '5px',
    boxSizing: 'border-box' as const,
    backgroundColor: errorFields.includes(field) ? '#f8d7da' : '#fff',
    borderColor: errorFields.includes(field) ? '#dc3545' : '#ccc',
    borderStyle: 'solid',
    borderWidth: '1px',
  });

  if (loading || !me) {
    return (
      <div style={{ maxWidth: '480px', margin: '40px auto', padding: '20px' }}>
        <p>読み込み中...</p>
      </div>
    );
  }

  if (stores.length === 0) {
    return (
      <div style={{ maxWidth: '480px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
        <h2>卓（テーブル・カウンター）</h2>
        <p style={{ color: '#666' }}>先に店舗設定から店舗を作成してください。</p>
        <BackToHomeButton onClick={() => navigate('/home')} />
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '480px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
      <h2>卓（テーブル・カウンター）</h2>

      <TopMessage messages={messages} isError />
      <TopMessage messages={successMessage} isError={false} />

      {view === 'select-store' && (
        <div style={{ marginBottom: '15px' }}>
          <p style={{ color: '#666' }}>卓を管理する店舗を選んでください。</p>
          {stores.map((store) => (
            <button
              key={store.id}
              type="button"
              onClick={() => selectStore(store.id)}
              style={{
                display: 'block',
                width: '100%',
                textAlign: 'left',
                padding: '12px 16px',
                marginBottom: '8px',
                border: '1px solid #ddd',
                borderRadius: '8px',
                background: '#fff',
                cursor: 'pointer',
              }}
            >
              {store.name}
            </button>
          ))}
        </div>
      )}

      {view === 'list' && selectedStoreId !== null && (
        <>
          {stores.length > 1 && (
            <p style={{ color: '#666', marginBottom: '15px' }}>
              店舗: <strong>{stores.find((s) => s.id === selectedStoreId)?.name}</strong>
            </p>
          )}

          <div style={{ display: 'flex', gap: '12px', marginBottom: '15px' }}>
            <div style={{ flex: 1 }}>
              <label style={{ display: 'block', marginBottom: '5px' }}>席種類で絞り込み:</label>
              <select
                value={seatTypeFilter}
                onChange={(e) => setSeatTypeFilter(e.target.value as SeatTypeFilter)}
                style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
              >
                <option value="ALL">すべて</option>
                <option value="TABLE">{SEAT_TYPE_LABELS.TABLE}</option>
                <option value="COUNTER">{SEAT_TYPE_LABELS.COUNTER}</option>
              </select>
            </div>
            <div style={{ flex: 1 }}>
              <label style={{ display: 'block', marginBottom: '5px' }}>有効/無効で絞り込み:</label>
              <select
                value={activeFilter}
                onChange={(e) => setActiveFilter(e.target.value as ActiveFilter)}
                style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
              >
                <option value="ALL">すべて</option>
                <option value="ACTIVE">有効のみ</option>
                <option value="INACTIVE">無効のみ</option>
              </select>
            </div>
          </div>

          <div style={{ marginBottom: '15px' }}>
            {tables.length === 0 && <p style={{ color: '#666' }}>卓がまだ登録されていません。</p>}
            {tables.length > 0 && visibleTables.length === 0 && (
              <p style={{ color: '#666' }}>条件に一致する卓がありません。</p>
            )}
            {visibleTables.map((table) => (
              <button
                key={table.id}
                type="button"
                onClick={() => openEdit(table)}
                style={{
                  display: 'block',
                  width: '100%',
                  textAlign: 'left',
                  padding: '12px 16px',
                  marginBottom: '8px',
                  border: '1px solid #ddd',
                  borderRadius: '8px',
                  background: table.active ? '#fff' : '#f5f5f5',
                  cursor: 'pointer',
                  opacity: table.active ? 1 : 0.6,
                }}
              >
                <div style={{ fontWeight: 600 }}>
                  {table.tableNo}
                  {table.area ? `（${table.area}）` : ''}
                  {!table.active && '　[無効]'}
                </div>
                <div style={{ fontSize: '13px', color: '#666' }}>
                  {SEAT_TYPE_LABELS[table.seatType]} ・ 席数 {table.seatCount} ・{' '}
                  {STATUS_LABELS[table.status] ?? table.status}
                </div>
              </button>
            ))}
          </div>

          {canEditStore(selectedStoreId) && (
            <button
              type="button"
              onClick={openCreate}
              style={{
                width: '100%',
                padding: '10px',
                marginBottom: '15px',
                backgroundColor: '#fff',
                color: '#007bff',
                border: '1px dashed #007bff',
                borderRadius: '4px',
                cursor: 'pointer',
              }}
            >
              ＋ 新しい卓を追加
            </button>
          )}

          {stores.length > 1 && (
            <button
              type="button"
              onClick={() => {
                resetMessages();
                setView('select-store');
              }}
              style={{
                width: '100%',
                padding: '10px',
                marginBottom: '15px',
                backgroundColor: '#fff',
                color: '#333',
                border: '1px solid #ccc',
                borderRadius: '4px',
                cursor: 'pointer',
              }}
            >
              ← 店舗を選び直す
            </button>
          )}
        </>
      )}

      {view === 'form' && (
        <form onSubmit={handleSubmit}>
          <FormField label="卓番号">
            <input
              type="text"
              value={form.tableNo}
              onChange={(e) => {
                setForm((prev) => ({ ...prev, tableNo: e.target.value }));
                clearFieldError('tableNo');
              }}
              required
              style={getInputStyle('tableNo')}
            />
          </FormField>
          <FormField label="席種類">
            <div style={{ marginTop: '5px', display: 'flex', gap: '16px' }}>
              {(['TABLE', 'COUNTER'] as const).map((seatType) => (
                <label key={seatType} style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <input
                    type="radio"
                    name="seatType"
                    checked={form.seatType === seatType}
                    onChange={() => handleSeatTypeChange(seatType)}
                  />
                  {SEAT_TYPE_LABELS[seatType]}
                </label>
              ))}
            </div>
          </FormField>
          <FormField label="席数">
            <input
              type="number"
              min={0}
              // 値が0のとき常に「0」を表示すると、消しても即座に0へ戻ってバックスペースで消せなく
              // なる（続けて入力すると「02」のようになる）ため、0の間だけ空欄で表示する。
              value={form.seatCount === 0 ? '' : form.seatCount}
              disabled={form.seatType === 'COUNTER'}
              onChange={(e) => {
                const value = e.target.value === '' ? 0 : Number(e.target.value);
                setForm((prev) => ({ ...prev, seatCount: value }));
                clearFieldError('seatCount');
              }}
              style={getInputStyle('seatCount')}
            />
            {form.seatType === 'COUNTER' && (
              <p style={{ fontSize: '12px', color: '#666', marginTop: '4px' }}>
                カウンター席は1席ごとに卓番号を分けて登録するため、席数は自動で1になります。
              </p>
            )}
          </FormField>
          <FormField label="エリア（任意）">
            <input
              type="text"
              placeholder="例: 1階カウンター"
              value={form.area}
              onChange={(e) => setForm((prev) => ({ ...prev, area: e.target.value }))}
              style={getInputStyle('area')}
            />
          </FormField>
          <div style={{ marginBottom: '15px' }}>
            <label>
              <input
                type="checkbox"
                checked={form.active}
                onChange={(e) => setForm((prev) => ({ ...prev, active: e.target.checked }))}
                style={{ marginRight: '8px' }}
              />
              有効にする
            </label>
          </div>

          <button
            type="submit"
            disabled={saving}
            style={{
              width: '100%',
              padding: '10px',
              backgroundColor: '#007bff',
              color: '#fff',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
            }}
          >
            {saving ? '処理中...' : '保存する'}
          </button>
          <BackToListButton onClick={backToList} />
        </form>
      )}

      <div style={{ marginTop: '20px' }}>
        <BackToHomeButton onClick={() => navigate('/home')} />
      </div>
    </div>
  );
};

const FormField: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div style={{ marginBottom: '15px' }}>
    <label style={{ display: 'block', marginBottom: '5px' }}>{label}:</label>
    {children}
  </div>
);

const BackToListButton: React.FC<{ onClick: () => void }> = ({ onClick }) => (
  <button
    type="button"
    onClick={onClick}
    style={{
      width: '100%',
      padding: '10px',
      marginTop: '10px',
      backgroundColor: '#fff',
      color: '#333',
      border: '1px solid #ccc',
      borderRadius: '4px',
      cursor: 'pointer',
    }}
  >
    ← 卓一覧に戻る
  </button>
);

const BackToHomeButton: React.FC<{ onClick: () => void }> = ({ onClick }) => (
  <button
    type="button"
    onClick={onClick}
    style={{
      width: '100%',
      padding: '10px',
      backgroundColor: '#6c757d',
      color: '#fff',
      border: 'none',
      borderRadius: '4px',
      cursor: 'pointer',
    }}
  >
    ホームに戻る
  </button>
);
