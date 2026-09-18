import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TopMessage } from '../components/TopMessage';
import { fetchMe, type Me } from '../api/session';
import { fetchStores, type Store } from '../api/stores';
import { fetchTables, type DiningTable, type SeatType } from '../api/tables';
import { fetchReservations, type Reservation } from '../api/reservations';
import { fetchMenuCategories, fetchMenuItems, type MenuCategory, type MenuItem } from '../api/menu';
import {
  fetchActiveTableSessions,
  openTableSession,
  fetchTableSessionDetail,
  submitOrder,
  updateOrderLine,
  cancelOrderLine,
  remakeOrderLine,
  serveOrderLine,
  type TableSession,
  type TableSessionDetail,
  type OrderLine,
  type CancelReason,
} from '../api/floor';

const TABLE_STATUS_LABELS: Record<string, string> = {
  EMPTY: '空席',
  OCCUPIED: '利用中',
  BILLING: '会計中',
};

const SEAT_TYPE_LABELS: Record<SeatType, string> = {
  COUNTER: 'カウンター',
  TABLE: 'テーブル',
};

const SERVE_STATUS_LABELS: Record<string, string> = {
  PENDING: '未提供',
  PREPARING: '調理中',
  SERVED: '提供済み',
  CANCELLED: '取消',
  REJECTED: '却下',
};

const CANCEL_REASON_OPTIONS: { value: CancelReason; label: string }[] = [
  { value: 'ORDER_MISTAKE', label: 'オーダーミス' },
  { value: 'QUALITY', label: '品質不良' },
  { value: 'DELAY', label: '提供遅延' },
  { value: 'WRONG_SERVE', label: '誤提供' },
  { value: 'SOLD_OUT', label: '品切れ' },
  { value: 'CUSTOMER', label: '客都合' },
  { value: 'OTHER', label: 'その他' },
];

function todayStr(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

function formatTime(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

type View = 'select-store' | 'board' | 'open' | 'order';
type SeatTypeFilter = 'ALL' | SeatType;
type ActiveFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';

/**
 * 注文管理（卓・注文）画面（FR-E01・E02・E03・E03b・E03c・E04・E07・FR-C07）。
 * 卓ボードで空席の卓をオープンし、卓ごとの注文入力・取消・作り直し・提供済み記録を行う。
 * 卓のクローズ（会計後）は会計・レジ（FR-G）の実装まで見送っている（04_architecture.md 追補参照）。
 */
export const FloorPage: React.FC = () => {
  const navigate = useNavigate();

  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);
  const [stores, setStores] = useState<Store[]>([]);
  const [storeId, setStoreId] = useState<number | null>(null);
  const [view, setView] = useState<View>('board');

  const [tables, setTables] = useState<DiningTable[]>([]);
  const [seatTypeFilter, setSeatTypeFilter] = useState<SeatTypeFilter>('ALL');
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>('ALL');
  const [sessions, setSessions] = useState<TableSession[]>([]);
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [categories, setCategories] = useState<MenuCategory[]>([]);
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);

  const [openingTable, setOpeningTable] = useState<DiningTable | null>(null);
  const [openPartySize, setOpenPartySize] = useState(2);
  const [openReservationId, setOpenReservationId] = useState<number | ''>('');

  const [detail, setDetail] = useState<TableSessionDetail | null>(null);
  const [cart, setCart] = useState<{ menuItemId: number; quantity: number; note: string }[]>([]);
  const [categoryFilter, setCategoryFilter] = useState<number | 'ALL'>('ALL');

  const [cancellingLine, setCancellingLine] = useState<OrderLine | null>(null);
  const [cancelReason, setCancelReason] = useState<CancelReason>('ORDER_MISTAKE');
  const [wasCooked, setWasCooked] = useState(false);

  const [messages, setMessages] = useState<string[]>([]);
  const [successMessage, setSuccessMessage] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const meResult = await fetchMe();
      if (cancelled) return;
      if (!meResult) {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        navigate('/staff', { replace: true });
        return;
      }
      setMe(meResult);

      const storeList = await fetchStores();
      if (cancelled) return;
      setStores(storeList);

      if (storeList.length === 1) {
        await selectStore(storeList[0].id);
      } else {
        setView('select-store');
      }
      setLoading(false);
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [navigate]);

  const resetMessages = () => {
    setMessages([]);
    setSuccessMessage('');
  };

  const canOperate = (targetStoreId: number) =>
    !!me &&
    (me.role === 'OWNER' ||
      ((me.role === 'MANAGER' || me.role === 'HALL') && me.stores.some((s) => s.id === targetStoreId)));

  const selectStore = async (id: number) => {
    setStoreId(id);
    await refreshBoard(id);
    setSeatTypeFilter('ALL');
    setActiveFilter('ALL');
    setView('board');
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

  const refreshBoard = async (id: number) => {
    const [tableList, sessionList] = await Promise.all([fetchTables(id), fetchActiveTableSessions(id)]);
    setTables(tableList);
    setSessions(sessionList);
  };

  const openOpenForm = async (table: DiningTable) => {
    resetMessages();
    setOpeningTable(table);
    setOpenPartySize(table.seatType === 'COUNTER' ? 1 : 2);
    setOpenReservationId('');
    if (storeId !== null) {
      const list = await fetchReservations(storeId, todayStr(), 1);
      setReservations(list.filter((r) => r.status === 'CONFIRMED'));
    }
    setView('open');
  };

  const submitOpen = async (e: React.FormEvent) => {
    e.preventDefault();
    if (storeId === null || openingTable === null) return;
    resetMessages();
    setSaving(true);
    try {
      const result = await openTableSession(storeId, {
        diningTableId: openingTable.id,
        partySize: openPartySize,
        reservationId: openReservationId === '' ? null : openReservationId,
      });
      if (!result.ok) {
        setMessages(result.errors.map((e2) => e2.message));
        return;
      }
      await refreshBoard(storeId);
      await openSessionOrder(result.data.id);
    } catch (error) {
      console.error('通信エラー:', error);
      setMessages(['サーバーとの通信に失敗しました。']);
    } finally {
      setSaving(false);
    }
  };

  const openSessionOrder = async (sessionId: number) => {
    if (storeId === null) return;
    resetMessages();
    const [d, cats, items] = await Promise.all([
      fetchTableSessionDetail(storeId, sessionId),
      categories.length > 0 ? Promise.resolve(categories) : fetchMenuCategories(storeId),
      menuItems.length > 0 ? Promise.resolve(menuItems) : fetchMenuItems(storeId),
    ]);
    if (!d) {
      setMessages(['卓の情報を取得できませんでした。']);
      return;
    }
    setDetail(d);
    setCategories(cats);
    setMenuItems(items);
    setCategoryFilter('ALL');
    setCart([]);
    setView('order');
  };

  const refreshOrder = async () => {
    if (storeId === null || detail === null) return;
    const d = await fetchTableSessionDetail(storeId, detail.session.id);
    if (d) setDetail(d);
  };

  const addToCart = (item: MenuItem) => {
    setCart((prev) => {
      const existing = prev.find((c) => c.menuItemId === item.id);
      if (existing) {
        return prev.map((c) => (c.menuItemId === item.id ? { ...c, quantity: c.quantity + 1 } : c));
      }
      return [...prev, { menuItemId: item.id, quantity: 1, note: '' }];
    });
  };

  const changeCartQuantity = (menuItemId: number, quantity: number) => {
    setCart((prev) =>
      quantity <= 0
        ? prev.filter((c) => c.menuItemId !== menuItemId)
        : prev.map((c) => (c.menuItemId === menuItemId ? { ...c, quantity } : c))
    );
  };

  const submitCart = async () => {
    if (storeId === null || detail === null || cart.length === 0) return;
    resetMessages();
    setSaving(true);
    try {
      const result = await submitOrder(
        storeId,
        detail.session.id,
        cart.map((c) => ({ menuItemId: c.menuItemId, quantity: c.quantity, note: c.note }))
      );
      if (!result.ok) {
        setMessages(result.errors.map((e2) => e2.message));
        return;
      }
      setDetail(result.data);
      setCart([]);
      setSuccessMessage('注文を送信しました。');
    } catch (error) {
      console.error('通信エラー:', error);
      setMessages(['サーバーとの通信に失敗しました。']);
    } finally {
      setSaving(false);
    }
  };

  const handleServe = async (line: OrderLine) => {
    if (storeId === null) return;
    resetMessages();
    const result = await serveOrderLine(storeId, line.id);
    if (!result.ok) {
      setMessages(result.errors.map((e2) => e2.message));
      return;
    }
    await refreshOrder();
  };

  const handleRemake = async (line: OrderLine) => {
    if (storeId === null) return;
    resetMessages();
    const result = await remakeOrderLine(storeId, line.id);
    if (!result.ok) {
      setMessages(result.errors.map((e2) => e2.message));
      return;
    }
    await refreshOrder();
    setSuccessMessage(`「${line.itemNameSnap}」の作り直しを注文しました。`);
  };

  const openCancelDialog = (line: OrderLine) => {
    resetMessages();
    setCancellingLine(line);
    setCancelReason('ORDER_MISTAKE');
    setWasCooked(line.serveStatus === 'SERVED' || line.serveStatus === 'PREPARING');
  };

  const submitCancel = async (e: React.FormEvent) => {
    e.preventDefault();
    if (storeId === null || cancellingLine === null) return;
    resetMessages();
    setSaving(true);
    try {
      const result = await cancelOrderLine(storeId, cancellingLine.id, {
        reason: cancelReason,
        wasCooked,
      });
      if (!result.ok) {
        setMessages(result.errors.map((e2) => e2.message));
        return;
      }
      setCancellingLine(null);
      await refreshOrder();
    } catch (error) {
      console.error('通信エラー:', error);
      setMessages(['サーバーとの通信に失敗しました。']);
    } finally {
      setSaving(false);
    }
  };

  const visibleMenuItems = menuItems.filter(
    (item) =>
      item.salesStatus === 'ON_SALE' && item.active && (categoryFilter === 'ALL' || item.categoryId === categoryFilter)
  );

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
        <h2>注文管理</h2>
        <p style={{ color: '#666' }}>先に店舗設定から店舗を作成してください。</p>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '520px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
      <h2>注文管理</h2>
      <TopMessage messages={messages} isError />
      <TopMessage messages={successMessage} isError={false} />

      {view === 'select-store' && (
        <div style={{ marginBottom: '15px' }}>
          <p style={{ color: '#666' }}>操作する店舗を選んでください。</p>
          {stores.map((store) => (
            <button
              key={store.id}
              type="button"
              onClick={() => selectStore(store.id)}
              style={boardButtonStyle('#fff')}
            >
              {store.name}
            </button>
          ))}
        </div>
      )}

      {view === 'board' && storeId !== null && (
        <>
          {stores.length > 1 && (
            <p style={{ color: '#666', marginBottom: '15px' }}>
              店舗: <strong>{stores.find((s) => s.id === storeId)?.name}</strong>
            </p>
          )}
          {tables.length > 0 && (
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
          )}
          {tables.length === 0 && <p style={{ color: '#666' }}>卓がまだ登録されていません。</p>}
          {tables.length > 0 && visibleTables.length === 0 && (
            <p style={{ color: '#666' }}>条件に一致する卓がありません。</p>
          )}
          {visibleTables.map((table) => {
            const session = sessions.find((s) => s.diningTableId === table.id);
            // 無効な卓は新規オープン不可（既にオープン済みのセッションがあれば、その注文の管理は続けられる）。
            const canOpen = table.status === 'EMPTY' && table.active;
            const clickable = canOperate(storeId) && (canOpen || session !== undefined);
            return (
              <button
                key={table.id}
                type="button"
                disabled={!clickable}
                onClick={() => (canOpen ? openOpenForm(table) : session && openSessionOrder(session.id))}
                style={{
                  ...boardButtonStyle(table.status === 'EMPTY' ? '#fff' : '#eef6ff'),
                  cursor: clickable ? 'pointer' : 'default',
                  opacity: table.active ? 1 : 0.6,
                }}
              >
                <div style={{ fontWeight: 600 }}>
                  {table.tableNo}
                  {table.area ? `（${table.area}）` : ''}
                  {!table.active && '　[無効]'}
                  　{SEAT_TYPE_LABELS[table.seatType]} ・ 席数 {table.seatCount}
                </div>
                <div style={{ fontSize: '13px', color: '#666' }}>
                  {TABLE_STATUS_LABELS[table.status] ?? table.status}
                  {session ? ` ・ ${session.partySize}名 ・ ${formatTime(session.openedAt)}〜` : ''}
                </div>
              </button>
            );
          })}
          {stores.length > 1 && (
            <button type="button" onClick={() => setView('select-store')} style={backButtonStyle}>
              ← 店舗を選び直す
            </button>
          )}
        </>
      )}

      {view === 'open' && openingTable !== null && (
        <form onSubmit={submitOpen}>
          <p>
            卓 <strong>{openingTable.tableNo}</strong> をオープンします。
          </p>
          <FormField label="人数">
            <input
              type="number"
              min={1}
              value={openPartySize}
              onChange={(e) => setOpenPartySize(Number(e.target.value))}
              style={inputStyle}
              required
            />
          </FormField>
          {reservations.length > 0 && (
            <FormField label="紐づける予約（任意）">
              <select
                value={openReservationId}
                onChange={(e) => setOpenReservationId(e.target.value === '' ? '' : Number(e.target.value))}
                style={inputStyle}
              >
                <option value="">予約なし（ウォークイン）</option>
                {reservations.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.guestName}様 {r.partySize}名（{r.reservedAt.slice(11, 16)}〜）
                  </option>
                ))}
              </select>
            </FormField>
          )}
          <button type="submit" disabled={saving} style={submitButtonStyle}>
            {saving ? '処理中...' : 'オープンする'}
          </button>
          <button type="button" onClick={() => setView('board')} style={backButtonStyle}>
            ← 戻る
          </button>
        </form>
      )}

      {view === 'order' && detail !== null && (
        <>
          <p>
            卓 <strong>{detail.session.tableNo}</strong>（{detail.session.partySize}名 ・{' '}
            {formatTime(detail.session.openedAt)}〜）
          </p>

          <h3 style={{ marginTop: '24px' }}>現在の注文</h3>
          {detail.lines.length === 0 && <p style={{ color: '#666' }}>まだ注文がありません。</p>}
          {detail.lines.map((line) => (
            <div key={line.id} style={lineCardStyle(line.serveStatus)}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <strong>
                  {line.itemNameSnap} × {line.quantity}
                </strong>
                <span style={{ fontSize: '12px', color: '#666' }}>{SERVE_STATUS_LABELS[line.serveStatus]}</span>
              </div>
              {line.note && <div style={{ fontSize: '13px', color: '#666' }}>メモ: {line.note}</div>}
              {line.serveStatus === 'CANCELLED' && (
                <div style={{ fontSize: '13px', color: '#666' }}>
                  取消理由: {CANCEL_REASON_OPTIONS.find((o) => o.value === line.cancelReason)?.label ?? line.cancelReason}
                </div>
              )}
              <div style={{ marginTop: '6px', display: 'flex', gap: '8px' }}>
                {(line.serveStatus === 'PENDING' || line.serveStatus === 'PREPARING') && (
                  <>
                    <button type="button" onClick={() => handleServe(line)} style={lineActionStyle}>
                      提供済みにする
                    </button>
                    <button type="button" onClick={() => openCancelDialog(line)} style={lineActionStyle}>
                      取消する
                    </button>
                  </>
                )}
                {line.serveStatus === 'SERVED' && (
                  <button type="button" onClick={() => openCancelDialog(line)} style={lineActionStyle}>
                    取消する
                  </button>
                )}
                {line.serveStatus === 'CANCELLED' && (
                  <button type="button" onClick={() => handleRemake(line)} style={lineActionStyle}>
                    作り直す
                  </button>
                )}
              </div>
            </div>
          ))}

          {cancellingLine !== null && (
            <form
              onSubmit={submitCancel}
              style={{ border: '1px solid #dc3545', borderRadius: '8px', padding: '12px', marginTop: '10px' }}
            >
              <p style={{ marginTop: 0 }}>
                「{cancellingLine.itemNameSnap}」を取消します。
                {cancellingLine.serveStatus === 'SERVED' && (
                  <strong style={{ color: '#dc3545' }}> 提供済みの品の取消です。</strong>
                )}
              </p>
              <FormField label="取消理由">
                <select
                  value={cancelReason}
                  onChange={(e) => setCancelReason(e.target.value as CancelReason)}
                  style={inputStyle}
                >
                  {CANCEL_REASON_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </FormField>
              <label style={{ display: 'block', marginBottom: '12px' }}>
                <input
                  type="checkbox"
                  checked={wasCooked}
                  onChange={(e) => setWasCooked(e.target.checked)}
                  style={{ marginRight: '8px' }}
                />
                調理済み（廃棄ロスになる）
              </label>
              <button type="submit" disabled={saving} style={submitButtonStyle}>
                {saving ? '処理中...' : '取消を確定する'}
              </button>
              <button type="button" onClick={() => setCancellingLine(null)} style={backButtonStyle}>
                キャンセル
              </button>
            </form>
          )}

          <h3 style={{ marginTop: '24px' }}>追加注文</h3>
          <div style={{ marginBottom: '10px' }}>
            <select
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value === 'ALL' ? 'ALL' : Number(e.target.value))}
              style={inputStyle}
            >
              <option value="ALL">すべてのカテゴリ</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
          {visibleMenuItems.map((item) => {
            const inCart = cart.find((c) => c.menuItemId === item.id);
            return (
              <div
                key={item.id}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  border: '1px solid #ddd',
                  borderRadius: '8px',
                  padding: '10px 12px',
                  marginBottom: '8px',
                }}
              >
                <div>
                  <div style={{ fontWeight: 600 }}>{item.name}</div>
                  <div style={{ fontSize: '13px', color: '#666' }}>{item.priceJpy}円</div>
                </div>
                {inCart ? (
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <button
                      type="button"
                      onClick={() => changeCartQuantity(item.id, inCart.quantity - 1)}
                      style={qtyButtonStyle}
                    >
                      −
                    </button>
                    <span>{inCart.quantity}</span>
                    <button
                      type="button"
                      onClick={() => changeCartQuantity(item.id, inCart.quantity + 1)}
                      style={qtyButtonStyle}
                    >
                      ＋
                    </button>
                  </div>
                ) : (
                  <button type="button" onClick={() => addToCart(item)} style={qtyButtonStyle}>
                    追加
                  </button>
                )}
              </div>
            );
          })}

          {cart.length > 0 && (
            <button type="button" onClick={submitCart} disabled={saving} style={submitButtonStyle}>
              {saving ? '送信中...' : `注文を送信する（${cart.reduce((n, c) => n + c.quantity, 0)}点）`}
            </button>
          )}

          <button type="button" onClick={() => setView('board')} style={backButtonStyle}>
            ← 卓一覧に戻る
          </button>
        </>
      )}
    </div>
  );
};

const FormField: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div style={{ marginBottom: '15px' }}>
    <label style={{ display: 'block', marginBottom: '5px' }}>{label}:</label>
    {children}
  </div>
);

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '8px',
  boxSizing: 'border-box',
  border: '1px solid #ccc',
  borderRadius: '4px',
};

const submitButtonStyle: React.CSSProperties = {
  width: '100%',
  padding: '10px',
  marginTop: '10px',
  backgroundColor: '#007bff',
  color: '#fff',
  border: 'none',
  borderRadius: '4px',
  cursor: 'pointer',
};

const backButtonStyle: React.CSSProperties = {
  width: '100%',
  padding: '10px',
  marginTop: '10px',
  backgroundColor: '#fff',
  color: '#333',
  border: '1px solid #ccc',
  borderRadius: '4px',
  cursor: 'pointer',
};

const lineActionStyle: React.CSSProperties = {
  padding: '6px 10px',
  fontSize: '13px',
  backgroundColor: '#fff',
  color: '#333',
  border: '1px solid #ccc',
  borderRadius: '4px',
  cursor: 'pointer',
};

const qtyButtonStyle: React.CSSProperties = {
  padding: '6px 12px',
  backgroundColor: '#fff',
  color: '#007bff',
  border: '1px solid #007bff',
  borderRadius: '4px',
  cursor: 'pointer',
};

function boardButtonStyle(background: string): React.CSSProperties {
  return {
    display: 'block',
    width: '100%',
    textAlign: 'left',
    padding: '12px 16px',
    marginBottom: '8px',
    border: '1px solid #ddd',
    borderRadius: '8px',
    background,
  };
}

function lineCardStyle(serveStatus: string): React.CSSProperties {
  return {
    border: '1px solid #ddd',
    borderRadius: '8px',
    padding: '10px 12px',
    marginBottom: '8px',
    opacity: serveStatus === 'CANCELLED' || serveStatus === 'REJECTED' ? 0.6 : 1,
  };
}
