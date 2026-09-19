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
import {
  fetchPaymentMethods,
  fetchChecks,
  createCheck,
  applyDiscount,
  addPayment,
  voidCheck,
  refundCheck,
  type GuestCheck,
  type PaymentMethod,
  type PaymentMethodType,
  type DiscountType,
} from '../api/checkout';

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

const PAYMENT_METHOD_LABELS: Record<PaymentMethodType, string> = {
  CASH: '現金',
  PAYPAY: 'PayPay',
  CREDIT_CARD: 'クレジットカード',
  RAKUTEN_PAY: '楽天ペイ',
};

const DISCOUNT_TYPE_OPTIONS: { value: DiscountType; label: string }[] = [
  { value: 'AMOUNT', label: '金額指定' },
  { value: 'RATE', label: '率指定（%）' },
  { value: 'COUPON', label: 'クーポン' },
  { value: 'ROUNDING', label: '端数調整' },
];

const CHECK_STATUS_LABELS: Record<string, string> = {
  OPEN: '会計中',
  FINALIZED: '会計済み',
  VOIDED: '取消済み',
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

type View = 'select-store' | 'board' | 'open' | 'order' | 'checkout';
type SeatTypeFilter = 'ALL' | SeatType;
type ActiveFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';
type ServeStatusFilter = 'ALL' | keyof typeof SERVE_STATUS_LABELS;

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
  const [serveStatusFilter, setServeStatusFilter] = useState<ServeStatusFilter>('ALL');

  const [cancellingLine, setCancellingLine] = useState<OrderLine | null>(null);
  const [cancelReason, setCancelReason] = useState<CancelReason>('ORDER_MISTAKE');
  const [wasCooked, setWasCooked] = useState(false);

  const [activeCheck, setActiveCheck] = useState<GuestCheck | null>(null);
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethod[]>([]);
  const [discountType, setDiscountType] = useState<DiscountType>('AMOUNT');
  const [discountValue, setDiscountValue] = useState(0);
  const [discountReason, setDiscountReason] = useState('');
  const [paymentMethodType, setPaymentMethodType] = useState<PaymentMethodType>('CASH');
  const [paymentAmount, setPaymentAmount] = useState(0);
  const [paymentTendered, setPaymentTendered] = useState(0);
  const [refundingOpen, setRefundingOpen] = useState(false);
  const [refundAmount, setRefundAmount] = useState(0);
  const [refundReason, setRefundReason] = useState('');
  const [refundReasonNote, setRefundReasonNote] = useState('');

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
    setServeStatusFilter('ALL');
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

  const openCheckout = async (sessionId?: number) => {
    const targetSessionId = sessionId ?? detail?.session.id;
    if (storeId === null || targetSessionId === undefined) return;
    resetMessages();
    setSaving(true);
    try {
      const needsDetail = detail === null || detail.session.id !== targetSessionId;
      const [methods, checks, sessionDetail] = await Promise.all([
        fetchPaymentMethods(storeId),
        fetchChecks(storeId, targetSessionId),
        needsDetail ? fetchTableSessionDetail(storeId, targetSessionId) : Promise.resolve(null),
      ]);
      setPaymentMethods(methods.filter((m) => m.enabled));
      if (sessionDetail) {
        setDetail(sessionDetail);
      }

      let check = checks.find((c) => c.status === 'OPEN') ?? null;
      if (!check) {
        const result = await createCheck(storeId, targetSessionId);
        if (!result.ok) {
          setMessages(result.errors.map((e2) => e2.message));
          return;
        }
        check = result.data;
      }
      setActiveCheck(check);
      setDiscountType('AMOUNT');
      setDiscountValue(0);
      setDiscountReason('');
      setPaymentAmount(check.balanceJpy);
      setPaymentTendered(check.balanceJpy);
      setView('checkout');
    } catch (error) {
      console.error('通信エラー:', error);
      setMessages(['サーバーとの通信に失敗しました。']);
    } finally {
      setSaving(false);
    }
  };

  const submitDiscount = async (e: React.FormEvent) => {
    e.preventDefault();
    if (storeId === null || activeCheck === null) return;
    resetMessages();
    setSaving(true);
    try {
      const result = await applyDiscount(storeId, activeCheck.id, {
        type: discountType,
        value: discountValue,
        reason: discountReason,
      });
      if (!result.ok) {
        setMessages(result.errors.map((e2) => e2.message));
        return;
      }
      setActiveCheck(result.data);
      setPaymentAmount(result.data.balanceJpy);
      setPaymentTendered(result.data.balanceJpy);
      setDiscountValue(0);
      setDiscountReason('');
    } catch (error) {
      console.error('通信エラー:', error);
      setMessages(['サーバーとの通信に失敗しました。']);
    } finally {
      setSaving(false);
    }
  };

  const submitPayment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (storeId === null || activeCheck === null) return;
    resetMessages();
    setSaving(true);
    try {
      const result = await addPayment(storeId, activeCheck.id, {
        methodType: paymentMethodType,
        amountJpy: paymentAmount,
        tenderedJpy: paymentMethodType === 'CASH' ? paymentTendered : undefined,
      });
      if (!result.ok) {
        setMessages(result.errors.map((e2) => e2.message));
        return;
      }
      setActiveCheck(result.data);
      setPaymentAmount(result.data.balanceJpy);
      setPaymentTendered(result.data.balanceJpy);
      if (result.data.status === 'FINALIZED') {
        setSuccessMessage('会計が完了しました。');
        if (storeId !== null) {
          await refreshBoard(storeId);
        }
      }
    } catch (error) {
      console.error('通信エラー:', error);
      setMessages(['サーバーとの通信に失敗しました。']);
    } finally {
      setSaving(false);
    }
  };

  const handleVoidCheck = async () => {
    if (storeId === null || activeCheck === null) return;
    resetMessages();
    setSaving(true);
    try {
      const result = await voidCheck(storeId, activeCheck.id);
      if (!result.ok) {
        setMessages(result.errors.map((e2) => e2.message));
        return;
      }
      setSuccessMessage('会計を取消しました。');
      setView('order');
      await refreshOrder();
      if (storeId !== null) {
        await refreshBoard(storeId);
      }
    } catch (error) {
      console.error('通信エラー:', error);
      setMessages(['サーバーとの通信に失敗しました。']);
    } finally {
      setSaving(false);
    }
  };

  const openRefundDialog = () => {
    if (activeCheck === null) return;
    resetMessages();
    setRefundAmount(activeCheck.totalJpy);
    setRefundReason('CUSTOMER');
    setRefundReasonNote('');
    setRefundingOpen(true);
  };

  const submitRefund = async (e: React.FormEvent) => {
    e.preventDefault();
    if (storeId === null || activeCheck === null) return;
    resetMessages();
    setSaving(true);
    try {
      const result = await refundCheck(storeId, activeCheck.id, {
        paymentId: null,
        amountJpy: refundAmount,
        reason: refundReason,
        reasonNote: refundReasonNote,
      });
      if (!result.ok) {
        setMessages(result.errors.map((e2) => e2.message));
        return;
      }
      setActiveCheck(result.data);
      setRefundingOpen(false);
      setSuccessMessage('返金を記録しました。');
    } catch (error) {
      console.error('通信エラー:', error);
      setMessages(['サーバーとの通信に失敗しました。']);
    } finally {
      setSaving(false);
    }
  };

  const hasBillableLines =
    detail !== null && detail.lines.some((l) => l.serveStatus !== 'CANCELLED' && l.serveStatus !== 'REJECTED');

  const visibleLines =
    detail === null
      ? []
      : detail.lines.filter((l) => serveStatusFilter === 'ALL' || l.serveStatus === serveStatusFilter);

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
                <label style={{ display: 'block', marginBottom: '5px' }}>卓種類で絞り込み:</label>
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
              <div
                key={table.id}
                role="button"
                tabIndex={clickable ? 0 : -1}
                onClick={() => {
                  if (!clickable) return;
                  if (canOpen) {
                    openOpenForm(table);
                  } else if (session) {
                    openSessionOrder(session.id);
                  }
                }}
                style={{
                  ...boardButtonStyle(table.status === 'EMPTY' ? '#fff' : '#eef6ff'),
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  cursor: clickable ? 'pointer' : 'default',
                  opacity: table.active ? 1 : 0.6,
                }}
              >
                <div>
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
                </div>
                {session && canOperate(storeId) && (
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      openCheckout(session.id);
                    }}
                    disabled={saving}
                    style={{ ...qtyButtonStyle, padding: '8px 12px', flexShrink: 0, marginLeft: '10px' }}
                  >
                    会計処理へ
                  </button>
                )}
              </div>
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

          <div style={currentOrderBoxStyle}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}
            >
              <h3 style={{ margin: 0 }}>現在の注文</h3>
              {hasBillableLines && (
                <button
                  type="button"
                  onClick={() => openCheckout()}
                  disabled={saving}
                  style={{ ...qtyButtonStyle, padding: '8px 16px' }}
                >
                  会計処理へ
                </button>
              )}
            </div>
            {detail.lines.length === 0 && <p style={{ color: '#666' }}>まだ注文がありません。</p>}
            {detail.lines.length > 0 && (
              <div style={{ marginBottom: '10px' }}>
                <label style={{ display: 'block', marginBottom: '5px' }}>注文状態で絞り込み:</label>
                <select
                  value={serveStatusFilter}
                  onChange={(e) => setServeStatusFilter(e.target.value as ServeStatusFilter)}
                  style={inputStyle}
                >
                  <option value="ALL">すべて</option>
                  {(Object.keys(SERVE_STATUS_LABELS) as (keyof typeof SERVE_STATUS_LABELS)[]).map((status) => (
                    <option key={status} value={status}>
                      {SERVE_STATUS_LABELS[status]}
                    </option>
                  ))}
                </select>
              </div>
            )}
            {detail.lines.length > 0 && visibleLines.length === 0 && (
              <p style={{ color: '#666' }}>条件に一致する注文明細がありません。</p>
            )}
            {visibleLines.map((line) => (
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
          </div>

          <div style={additionalOrderBoxStyle}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}
            >
              <h3 style={{ margin: 0 }}>追加注文</h3>
              {cart.length > 0 && (
                <button
                  type="button"
                  onClick={submitCart}
                  disabled={saving}
                  style={{ ...qtyButtonStyle, padding: '8px 16px' }}
                >
                  {saving ? '送信中...' : `注文送信（${cart.reduce((n, c) => n + c.quantity, 0)}点）`}
                </button>
              )}
            </div>
            {cart.length > 0 && (
              <div style={{ marginBottom: '15px' }}>
                {cart.map((c) => {
                  const item = menuItems.find((m) => m.id === c.menuItemId);
                  if (!item) return null;
                  return (
                    <div
                      key={c.menuItemId}
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        border: '1px solid #007bff',
                        borderRadius: '8px',
                        padding: '10px 12px',
                        marginBottom: '8px',
                        backgroundColor: '#eef6ff',
                      }}
                    >
                      <div>
                        <div style={{ fontWeight: 600 }}>{item.name}</div>
                        <div style={{ fontSize: '13px', color: '#666' }}>{item.priceJpy}円</div>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <button
                          type="button"
                          onClick={() => changeCartQuantity(item.id, c.quantity - 1)}
                          style={qtyButtonStyle}
                        >
                          −
                        </button>
                        <span>{c.quantity}</span>
                        <button
                          type="button"
                          onClick={() => changeCartQuantity(item.id, c.quantity + 1)}
                          style={qtyButtonStyle}
                        >
                          ＋
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
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
          </div>

          <button type="button" onClick={() => setView('board')} style={backButtonStyle}>
            ← 卓一覧に戻る
          </button>
        </>
      )}

      {view === 'checkout' && activeCheck !== null && (
        <>
          <p>
            卓 {detail?.session.tableNo} ・ 会計 #{activeCheck.seqInSession}（
            {CHECK_STATUS_LABELS[activeCheck.status] ?? activeCheck.status}）
          </p>

          <h3 style={{ marginTop: '20px' }}>明細</h3>
          {activeCheck.lines.map((line) => (
            <div key={line.id} style={lineCardStyle('OPEN')}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>
                  {line.itemNameSnap} × {line.quantity}
                </span>
                <span>{line.amountJpy}円</span>
              </div>
            </div>
          ))}

          <div style={{ marginTop: '16px', borderTop: '1px solid #ddd', paddingTop: '12px' }}>
            <div style={summaryRowStyle}>
              <span>税抜小計</span>
              <span>{activeCheck.subtotalJpy}円</span>
            </div>
            {activeCheck.discountTotalJpy > 0 && (
              <div style={summaryRowStyle}>
                <span>値引き</span>
                <span>−{activeCheck.discountTotalJpy}円</span>
              </div>
            )}
            <div style={summaryRowStyle}>
              <span>消費税</span>
              <span>{activeCheck.taxTotalJpy}円</span>
            </div>
            <div style={{ ...summaryRowStyle, fontWeight: 600, fontSize: '18px' }}>
              <span>合計</span>
              <span>{activeCheck.totalJpy}円</span>
            </div>
            {activeCheck.paidTotalJpy > 0 && (
              <div style={summaryRowStyle}>
                <span>入金済み</span>
                <span>{activeCheck.paidTotalJpy}円</span>
              </div>
            )}
            {activeCheck.status === 'OPEN' && (
              <div style={{ ...summaryRowStyle, fontWeight: 600 }}>
                <span>残額</span>
                <span>{activeCheck.balanceJpy}円</span>
              </div>
            )}
          </div>

          {activeCheck.discounts.length > 0 && (
            <div style={{ marginTop: '12px', fontSize: '13px', color: '#666' }}>
              {activeCheck.discounts.map((d) => (
                <div key={d.id}>
                  値引き: {d.amountJpy}円（{DISCOUNT_TYPE_OPTIONS.find((o) => o.value === d.type)?.label}
                  {d.reason ? ` ・ ${d.reason}` : ''}）
                </div>
              ))}
            </div>
          )}

          {activeCheck.status === 'OPEN' && (
            <form onSubmit={submitDiscount} style={sectionBoxStyle}>
              <h3 style={{ marginTop: 0 }}>値引き・クーポン</h3>
              <FormField label="種類">
                <select
                  value={discountType}
                  onChange={(e) => setDiscountType(e.target.value as DiscountType)}
                  style={inputStyle}
                >
                  {DISCOUNT_TYPE_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </FormField>
              <FormField label={discountType === 'RATE' ? '割引率（%）' : '金額（円）'}>
                <input
                  type="number"
                  value={discountValue === 0 ? '' : discountValue}
                  onChange={(e) => setDiscountValue(e.target.value === '' ? 0 : Number(e.target.value))}
                  style={inputStyle}
                />
              </FormField>
              <FormField label="理由（任意）">
                <input
                  type="text"
                  value={discountReason}
                  onChange={(e) => setDiscountReason(e.target.value)}
                  style={inputStyle}
                />
              </FormField>
              <button type="submit" disabled={saving || discountValue === 0} style={submitButtonStyle}>
                {saving ? '処理中...' : '値引きを適用する'}
              </button>
            </form>
          )}

          {activeCheck.status === 'OPEN' && activeCheck.balanceJpy > 0 && (
            <form onSubmit={submitPayment} style={sectionBoxStyle}>
              <h3 style={{ marginTop: 0 }}>支払いを記録</h3>
              <FormField label="決済手段">
                <select
                  value={paymentMethodType}
                  onChange={(e) => setPaymentMethodType(e.target.value as PaymentMethodType)}
                  style={inputStyle}
                >
                  {paymentMethods.map((m) => (
                    <option key={m.methodType} value={m.methodType}>
                      {m.displayName || PAYMENT_METHOD_LABELS[m.methodType]}
                    </option>
                  ))}
                </select>
              </FormField>
              {paymentMethods.length === 0 && (
                <p style={{ color: '#dc3545', fontSize: '13px' }}>
                  有効な決済手段がありません。店舗設定の「決済手段」から有効化してください。
                </p>
              )}
              <FormField label="金額（円）">
                <input
                  type="number"
                  value={paymentAmount === 0 ? '' : paymentAmount}
                  onChange={(e) => setPaymentAmount(e.target.value === '' ? 0 : Number(e.target.value))}
                  style={inputStyle}
                />
              </FormField>
              {paymentMethodType === 'CASH' && (
                <FormField label="預り金（円）">
                  <input
                    type="number"
                    value={paymentTendered === 0 ? '' : paymentTendered}
                    onChange={(e) => setPaymentTendered(e.target.value === '' ? 0 : Number(e.target.value))}
                    style={inputStyle}
                  />
                  {paymentTendered > paymentAmount && (
                    <p style={{ fontSize: '13px', color: '#666', marginTop: '4px' }}>
                      お釣り: {paymentTendered - paymentAmount}円
                    </p>
                  )}
                </FormField>
              )}
              <button
                type="submit"
                disabled={saving || paymentMethods.length === 0 || paymentAmount <= 0}
                style={submitButtonStyle}
              >
                {saving ? '処理中...' : 'この支払いを記録する'}
              </button>
            </form>
          )}

          {activeCheck.payments.length > 0 && (
            <div style={{ marginTop: '16px' }}>
              <h3>支払い履歴</h3>
              {activeCheck.payments.map((p) => (
                <div key={p.id} style={{ fontSize: '13px', color: '#666', marginBottom: '4px' }}>
                  {PAYMENT_METHOD_LABELS[p.methodType]}: {p.amountJpy}円
                  {p.methodType === 'CASH' && p.changeJpy ? `（お釣り${p.changeJpy}円）` : ''}
                </div>
              ))}
            </div>
          )}

          {activeCheck.status === 'OPEN' && (
            <button type="button" onClick={handleVoidCheck} disabled={saving} style={backButtonStyle}>
              会計を取消する
            </button>
          )}

          {activeCheck.status === 'FINALIZED' && !refundingOpen && (
            <button type="button" onClick={openRefundDialog} style={backButtonStyle}>
              返金する
            </button>
          )}

          {refundingOpen && (
            <form onSubmit={submitRefund} style={sectionBoxStyle}>
              <h3 style={{ marginTop: 0 }}>返金</h3>
              <FormField label="金額（円）">
                <input
                  type="number"
                  value={refundAmount === 0 ? '' : refundAmount}
                  onChange={(e) => setRefundAmount(e.target.value === '' ? 0 : Number(e.target.value))}
                  style={inputStyle}
                />
              </FormField>
              <FormField label="理由">
                <input
                  type="text"
                  value={refundReason}
                  onChange={(e) => setRefundReason(e.target.value)}
                  required
                  style={inputStyle}
                />
              </FormField>
              <FormField label="補足（任意）">
                <input
                  type="text"
                  value={refundReasonNote}
                  onChange={(e) => setRefundReasonNote(e.target.value)}
                  style={inputStyle}
                />
              </FormField>
              <button type="submit" disabled={saving} style={submitButtonStyle}>
                {saving ? '処理中...' : '返金を記録する'}
              </button>
              <button type="button" onClick={() => setRefundingOpen(false)} style={backButtonStyle}>
                キャンセル
              </button>
            </form>
          )}

          {activeCheck.refunds.length > 0 && (
            <div style={{ marginTop: '16px' }}>
              <h3>返金履歴</h3>
              {activeCheck.refunds.map((r) => (
                <div key={r.id} style={{ fontSize: '13px', color: '#666', marginBottom: '4px' }}>
                  {r.amountJpy}円（{r.reason}）
                </div>
              ))}
            </div>
          )}

          <button
            type="button"
            onClick={() => {
              setActiveCheck(null);
              setRefundingOpen(false);
              setView('board');
            }}
            style={backButtonStyle}
          >
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

const summaryRowStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  padding: '4px 0',
};

const sectionBoxStyle: React.CSSProperties = {
  border: '1px solid #ddd',
  borderRadius: '8px',
  padding: '12px',
  marginTop: '16px',
};

const currentOrderBoxStyle: React.CSSProperties = {
  border: '1px solid #ccc',
  borderRadius: '8px',
  padding: '16px',
  marginTop: '24px',
  backgroundColor: '#f5f5f5',
};

const additionalOrderBoxStyle: React.CSSProperties = {
  border: '1px solid #99c7ff',
  borderRadius: '8px',
  padding: '16px',
  marginTop: '24px',
  backgroundColor: '#f4f9ff',
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

const SERVE_STATUS_BACKGROUND_COLORS: Record<string, string> = {
  PENDING: '#fff',
  PREPARING: '#fff8e1',
  SERVED: '#e8f5e9',
  CANCELLED: '#f5f5f5',
  REJECTED: '#fdecea',
};

function lineCardStyle(serveStatus: string): React.CSSProperties {
  return {
    border: '1px solid #ddd',
    borderRadius: '8px',
    padding: '10px 12px',
    marginBottom: '8px',
    backgroundColor: SERVE_STATUS_BACKGROUND_COLORS[serveStatus] ?? '#fff',
    opacity: serveStatus === 'CANCELLED' || serveStatus === 'REJECTED' ? 0.6 : 1,
  };
}
