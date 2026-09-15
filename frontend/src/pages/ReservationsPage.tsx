import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TopMessage } from '../components/TopMessage';
import { fetchMe, type Me } from '../api/session';
import { fetchStores, type Store } from '../api/stores';
import {
  fetchReservations,
  createReservation,
  updateReservation,
  updateReservationStatus,
  type Reservation,
  type ReservationRequest,
  type ReservationStatus,
} from '../api/reservations';

const STATUS_LABELS: Record<ReservationStatus, string> = {
  REQUESTED: '申込済（承認待ち）',
  CONFIRMED: '確定',
  SEATED: '来店済',
  DONE: '会計完了',
  CANCELLED: 'キャンセル',
  NO_SHOW: '無断キャンセル',
};

const STATUS_COLORS: Record<ReservationStatus, string> = {
  REQUESTED: '#ffc107',
  CONFIRMED: '#198754',
  SEATED: '#0d6efd',
  DONE: '#6c757d',
  CANCELLED: '#dc3545',
  NO_SHOW: '#dc3545',
};

const CHANNEL_LABELS: Record<string, string> = {
  WEB: 'Web予約',
  PHONE: '電話予約',
  WALK_IN: '当日ウォークイン',
};

function todayStr(): string {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function addDays(dateStr: string, days: number): string {
  const d = new Date(`${dateStr}T00:00:00`);
  d.setDate(d.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function toDateTimeLocal(iso: string): string {
  // "2026-09-15T18:30:00" -> "2026-09-15T18:30"（<input type="datetime-local">用）
  return iso.slice(0, 16);
}

function formatReservedAt(iso: string): string {
  const d = new Date(iso);
  const weekday = ['日', '月', '火', '水', '木', '金', '土'][d.getDay()];
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${d.getMonth() + 1}/${d.getDate()}（${weekday}） ${hh}:${mm}`;
}

function formatDateOnly(dateStr: string): string {
  const d = new Date(`${dateStr}T00:00:00`);
  const weekday = ['日', '月', '火', '水', '木', '金', '土'][d.getDay()];
  return `${d.getMonth() + 1}/${d.getDate()}（${weekday}）`;
}

const EMPTY_FORM: ReservationRequest = {
  reservedAt: '',
  partySize: 2,
  guestName: '',
  guestPhone: '',
  guestEmail: '',
  requestNote: '',
  channel: 'PHONE',
};

type View = 'list' | 'form' | 'detail';
type StoreFilter = number | 'ALL';

/**
 * 予約台帳（FR-C01・C02・C09）。電話予約・当日ウォークインの登録・変更・キャンセルを行う。
 * Web予約フォーム（FR-C03〜C06）はここでは扱わない（別途対応）。
 * 経営管理者は全店、店長・ホールは自分の所属店舗のみ閲覧・編集できる（ReservationService・
 * StoreAccessGuard#requireCanManageReservations）。所属店舗が複数ある場合は「すべての店舗」
 * を横断表示でき、一覧・詳細に店舗名を表示する（2026-09-16追補）。
 */
export const ReservationsPage: React.FC = () => {
  const navigate = useNavigate();

  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);
  const [stores, setStores] = useState<Store[]>([]);
  const [storeFilter, setStoreFilter] = useState<StoreFilter>('ALL');
  const [date, setDate] = useState(todayStr());
  const [days, setDays] = useState<1 | 7>(1);
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [view, setView] = useState<View>('list');

  const [editingId, setEditingId] = useState<number | null>(null);
  const [formStoreId, setFormStoreId] = useState<number | null>(null);
  const [form, setForm] = useState<ReservationRequest>(EMPTY_FORM);
  const [detail, setDetail] = useState<Reservation | null>(null);
  const [cancelReason, setCancelReason] = useState('');

  const [messages, setMessages] = useState<string[]>([]);
  const [errorFields, setErrorFields] = useState<string[]>([]);
  const [successMessage, setSuccessMessage] = useState('');
  const [saving, setSaving] = useState(false);

  // 閲覧・編集できる店舗の集合。経営管理者は自テナント全店舗、店長・ホールは自分の所属店舗のみ
  // （GET /api/v1/stores はホールを絞り込まないため、ここでは me.stores を正とする）。
  const accessibleStores: { id: number; name: string }[] =
    me?.role === 'OWNER' ? stores : (me?.stores ?? []);

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

      if (storeList.length > 0) {
        const myStores = meResult.role === 'OWNER' ? storeList : meResult.stores;
        const initialFilter: StoreFilter = myStores.length === 1 ? myStores[0].id : 'ALL';
        setStoreFilter(initialFilter);
        setReservations(await fetchReservations(initialFilter, todayStr(), 1));
      }
      setLoading(false);
    })();
    return () => {
      cancelled = true;
    };
  }, [navigate]);

  const canEditStore = (storeId: number) =>
    !!me &&
    (me.role === 'OWNER' ||
      ((me.role === 'MANAGER' || me.role === 'HALL') && me.stores.some((s) => s.id === storeId)));

  const reload = async (filter: StoreFilter, d: string, n: 1 | 7) => {
    setReservations(await fetchReservations(filter, d, n));
  };

  const resetMessages = () => {
    setMessages([]);
    setErrorFields([]);
    setSuccessMessage('');
  };

  const changeStoreFilter = async (filter: StoreFilter) => {
    setStoreFilter(filter);
    await reload(filter, date, days);
  };

  const changeDate = async (newDate: string) => {
    setDate(newDate);
    await reload(storeFilter, newDate, days);
  };

  const changeDays = async (n: 1 | 7) => {
    setDays(n);
    await reload(storeFilter, date, n);
  };

  const openCreate = () => {
    resetMessages();
    setEditingId(null);
    setFormStoreId(storeFilter !== 'ALL' ? storeFilter : (accessibleStores[0]?.id ?? null));
    setForm({ ...EMPTY_FORM, reservedAt: `${date}T18:00` });
    setView('form');
  };

  const openDetail = (r: Reservation) => {
    resetMessages();
    setDetail(r);
    setCancelReason('');
    setView('detail');
  };

  const openEditFromDetail = (r: Reservation) => {
    resetMessages();
    setEditingId(r.id);
    setFormStoreId(r.storeId);
    setForm({
      reservedAt: toDateTimeLocal(r.reservedAt),
      partySize: r.partySize,
      guestName: r.guestName,
      guestPhone: r.guestPhone ?? '',
      guestEmail: r.guestEmail ?? '',
      requestNote: r.requestNote ?? '',
      channel: r.channel === 'WALK_IN' ? 'WALK_IN' : 'PHONE',
    });
    setView('form');
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
    if (formStoreId === null) return;
    resetMessages();
    setSaving(true);
    try {
      const body: ReservationRequest = { ...form, reservedAt: `${form.reservedAt}:00` };
      const result =
        editingId === null
          ? await createReservation(formStoreId, body)
          : await updateReservation(formStoreId, editingId, body);

      if (result.ok) {
        await reload(storeFilter, date, days);
        setView('list');
        setSuccessMessage(`「${result.data.guestName}」様の予約を保存しました。`);
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

  const handleStatusChange = async (status: ReservationStatus, reason?: string) => {
    if (detail === null) return;
    resetMessages();
    setSaving(true);
    try {
      const result = await updateReservationStatus(detail.storeId, detail.id, status, reason);
      if (result.ok) {
        await reload(storeFilter, date, days);
        setView('list');
        setSuccessMessage('予約の状態を更新しました。');
        return;
      }
      setMessages(result.errors.map((err) => err.message));
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
        <h2>予約</h2>
        <p style={{ color: '#666' }}>先に店舗設定から店舗を作成してください。</p>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '480px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
      <h2>予約</h2>

      <TopMessage messages={messages} isError />
      <TopMessage messages={successMessage} isError={false} />

      {view === 'list' && (
        <>
          {accessibleStores.length > 1 && (
            <div style={{ marginBottom: '15px' }}>
              <label style={{ display: 'block', marginBottom: '5px' }}>店舗で絞り込み:</label>
              <select
                value={storeFilter}
                onChange={(e) =>
                  changeStoreFilter(e.target.value === 'ALL' ? 'ALL' : Number(e.target.value))
                }
                style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
              >
                <option value="ALL">すべての店舗</option>
                {accessibleStores.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>
          )}

          <div style={{ display: 'flex', gap: '8px', marginBottom: '10px', alignItems: 'center' }}>
            <button type="button" onClick={() => changeDate(addDays(date, -days))} style={navButtonStyle}>
              ← 前
            </button>
            <input
              type="date"
              value={date}
              onChange={(e) => changeDate(e.target.value)}
              style={{ flex: 1, padding: '8px', boxSizing: 'border-box' }}
            />
            <button type="button" onClick={() => changeDate(addDays(date, days))} style={navButtonStyle}>
              次 →
            </button>
          </div>
          {days === 7 && (
            <p style={{ color: '#666', marginBottom: '10px', fontSize: '13px' }}>
              表示期間: {formatDateOnly(date)} 〜 {formatDateOnly(addDays(date, 6))}
            </p>
          )}
          <div style={{ display: 'flex', gap: '8px', marginBottom: '15px' }}>
            <button
              type="button"
              onClick={() => changeDays(1)}
              style={{ ...toggleButtonStyle, ...(days === 1 ? toggleButtonActiveStyle : {}) }}
            >
              日表示
            </button>
            <button
              type="button"
              onClick={() => changeDays(7)}
              style={{ ...toggleButtonStyle, ...(days === 7 ? toggleButtonActiveStyle : {}) }}
            >
              週表示
            </button>
          </div>

          <div style={{ marginBottom: '15px' }}>
            {reservations.length === 0 && (
              <p style={{ color: '#666' }}>この期間の予約はまだありません。</p>
            )}
            {reservations.map((r) => (
              <button
                key={r.id}
                type="button"
                onClick={() => openDetail(r)}
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
                <div style={{ fontWeight: 600, display: 'flex', justifyContent: 'space-between' }}>
                  <span>
                    {formatReservedAt(r.reservedAt)} ・ {r.guestName}様 ・ {r.partySize}名
                  </span>
                  <span style={{ color: STATUS_COLORS[r.status], fontSize: '13px' }}>
                    {STATUS_LABELS[r.status]}
                  </span>
                </div>
                <div style={{ fontSize: '13px', color: '#666' }}>
                  {r.storeName} ・ {CHANNEL_LABELS[r.channel] ?? r.channel}
                  {r.guestPhone ? ` ・ ${r.guestPhone}` : ''}
                </div>
              </button>
            ))}
          </div>

          {accessibleStores.length > 0 && (
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
              ＋ 新しい予約を登録
            </button>
          )}
        </>
      )}

      {view === 'detail' && detail !== null && (
        <div>
          <div style={{ marginBottom: '15px' }}>
            <div style={{ fontWeight: 600, fontSize: '16px' }}>{detail.guestName} 様</div>
            <div style={{ color: '#666', marginTop: '4px' }}>{formatReservedAt(detail.reservedAt)}</div>
            <div style={{ marginTop: '8px' }}>店舗: {detail.storeName}</div>
            <div>人数: {detail.partySize}名</div>
            {detail.guestPhone && <div>電話番号: {detail.guestPhone}</div>}
            {detail.guestEmail && <div>メールアドレス: {detail.guestEmail}</div>}
            {detail.requestNote && <div>ご要望: {detail.requestNote}</div>}
            <div>予約経路: {CHANNEL_LABELS[detail.channel] ?? detail.channel}</div>
            <div>
              状態: <span style={{ color: STATUS_COLORS[detail.status] }}>{STATUS_LABELS[detail.status]}</span>
            </div>
            {detail.cancelledReason && <div>キャンセル理由: {detail.cancelledReason}</div>}
          </div>

          {canEditStore(detail.storeId) && (detail.status === 'REQUESTED' || detail.status === 'CONFIRMED') && (
            <>
              {detail.status === 'REQUESTED' && (
                <button
                  type="button"
                  disabled={saving}
                  onClick={() => handleStatusChange('CONFIRMED')}
                  style={primaryButtonStyle}
                >
                  承認する
                </button>
              )}
              <button type="button" onClick={() => openEditFromDetail(detail)} style={primaryOutlineButtonStyle}>
                内容を編集する
              </button>
              <div style={{ marginBottom: '15px' }}>
                <label style={{ display: 'block', marginBottom: '5px' }}>
                  {detail.status === 'REQUESTED' ? '却下理由:' : 'キャンセル理由:'}
                </label>
                <input
                  type="text"
                  value={cancelReason}
                  onChange={(e) => setCancelReason(e.target.value)}
                  style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
                />
              </div>
              <button
                type="button"
                disabled={saving}
                onClick={() => handleStatusChange('CANCELLED', cancelReason)}
                style={dangerButtonStyle}
              >
                {detail.status === 'REQUESTED' ? 'この申込を却下する' : 'この予約をキャンセルする'}
              </button>
              {detail.status === 'CONFIRMED' && (
                <button
                  type="button"
                  disabled={saving}
                  onClick={() => handleStatusChange('NO_SHOW')}
                  style={dangerOutlineButtonStyle}
                >
                  無断キャンセル（来店なし）として記録する
                </button>
              )}
            </>
          )}

          <BackToListButton onClick={backToList} />
        </div>
      )}

      {view === 'form' && (
        <form onSubmit={handleSubmit}>
          {editingId === null && accessibleStores.length > 1 && (
            <FormField label="店舗">
              <select
                value={formStoreId ?? ''}
                onChange={(e) => setFormStoreId(Number(e.target.value))}
                required
                style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
              >
                {accessibleStores.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </FormField>
          )}
          {editingId !== null && (
            // 店舗は登録後に変更できないため、編集画面では読み取り専用で表示する（新規登録は選択可）。
            <FormField label="店舗">
              <input
                type="text"
                value={accessibleStores.find((s) => s.id === formStoreId)?.name ?? ''}
                disabled
                style={{ width: '100%', padding: '8px', boxSizing: 'border-box', backgroundColor: '#f0f0f0' }}
              />
            </FormField>
          )}
          {editingId === null && (
            <FormField label="予約経路">
              <div style={{ marginTop: '5px', display: 'flex', gap: '16px' }}>
                {(['PHONE', 'WALK_IN'] as const).map((channel) => (
                  <label key={channel} style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <input
                      type="radio"
                      name="channel"
                      checked={form.channel === channel}
                      onChange={() => setForm((prev) => ({ ...prev, channel }))}
                    />
                    {CHANNEL_LABELS[channel]}
                  </label>
                ))}
              </div>
            </FormField>
          )}
          <FormField label="予約日時">
            <input
              type="datetime-local"
              value={form.reservedAt}
              onChange={(e) => {
                setForm((prev) => ({ ...prev, reservedAt: e.target.value }));
                clearFieldError('reservedAt');
              }}
              required
              style={getInputStyle('reservedAt')}
            />
          </FormField>
          <FormField label="人数">
            <input
              type="number"
              min={1}
              value={form.partySize === 0 ? '' : form.partySize}
              onChange={(e) => {
                const value = e.target.value === '' ? 0 : Number(e.target.value);
                setForm((prev) => ({ ...prev, partySize: value }));
                clearFieldError('partySize');
              }}
              style={getInputStyle('partySize')}
            />
          </FormField>
          <FormField label="氏名">
            <input
              type="text"
              value={form.guestName}
              onChange={(e) => {
                setForm((prev) => ({ ...prev, guestName: e.target.value }));
                clearFieldError('guestName');
              }}
              required
              style={getInputStyle('guestName')}
            />
          </FormField>
          <FormField label={form.channel === 'PHONE' ? '電話番号' : '電話番号（任意）'}>
            <input
              type="text"
              value={form.guestPhone}
              onChange={(e) => {
                setForm((prev) => ({ ...prev, guestPhone: e.target.value }));
                clearFieldError('guestPhone');
              }}
              required={form.channel === 'PHONE'}
              style={getInputStyle('guestPhone')}
            />
          </FormField>
          <FormField label="メールアドレス（任意）">
            <input
              type="email"
              value={form.guestEmail}
              onChange={(e) => setForm((prev) => ({ ...prev, guestEmail: e.target.value }))}
              style={getInputStyle('guestEmail')}
            />
          </FormField>
          <FormField label="ご要望（任意。アレルギー等）">
            <textarea
              value={form.requestNote}
              onChange={(e) => setForm((prev) => ({ ...prev, requestNote: e.target.value }))}
              rows={3}
              style={getInputStyle('requestNote')}
            />
          </FormField>

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
    </div>
  );
};

const navButtonStyle: React.CSSProperties = {
  padding: '8px 12px',
  backgroundColor: '#fff',
  color: '#333',
  border: '1px solid #ccc',
  borderRadius: '4px',
  cursor: 'pointer',
};

const toggleButtonStyle: React.CSSProperties = {
  flex: 1,
  padding: '8px',
  backgroundColor: '#fff',
  color: '#333',
  border: '1px solid #ccc',
  borderRadius: '4px',
  cursor: 'pointer',
};

const toggleButtonActiveStyle: React.CSSProperties = {
  backgroundColor: '#007bff',
  color: '#fff',
  border: '1px solid #007bff',
};

const primaryButtonStyle: React.CSSProperties = {
  width: '100%',
  padding: '10px',
  marginBottom: '15px',
  backgroundColor: '#007bff',
  color: '#fff',
  border: 'none',
  borderRadius: '4px',
  cursor: 'pointer',
};

const primaryOutlineButtonStyle: React.CSSProperties = {
  width: '100%',
  padding: '10px',
  marginBottom: '15px',
  backgroundColor: '#fff',
  color: '#007bff',
  border: '1px solid #007bff',
  borderRadius: '4px',
  cursor: 'pointer',
};

const dangerButtonStyle: React.CSSProperties = {
  width: '100%',
  padding: '10px',
  marginBottom: '10px',
  backgroundColor: '#dc3545',
  color: '#fff',
  border: 'none',
  borderRadius: '4px',
  cursor: 'pointer',
};

const dangerOutlineButtonStyle: React.CSSProperties = {
  width: '100%',
  padding: '10px',
  marginBottom: '15px',
  backgroundColor: '#fff',
  color: '#dc3545',
  border: '1px solid #dc3545',
  borderRadius: '4px',
  cursor: 'pointer',
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
    ← 予約一覧に戻る
  </button>
);
