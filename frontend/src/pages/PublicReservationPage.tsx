import React, { useEffect, useState } from 'react';
import { TopMessage } from '../components/TopMessage';
import {
  fetchPublicStores,
  createPublicReservation,
  type PublicStore,
  type PublicReservationRequest,
  type PublicReservationResult,
} from '../api/publicReservations';

const EMPTY_FORM: PublicReservationRequest = {
  reservedAt: '',
  partySize: 2,
  guestName: '',
  guestPhone: '',
  guestEmail: '',
  requestNote: '',
};

type View = 'loading' | 'no-store' | 'select-store' | 'form' | 'done';

/**
 * お客様向けWeb予約フォーム（ログイン不要。FR-C03・C04・C06）。
 * ルーティングの入口（`/`）。04_architecture.md §6.1 2026-09-15追補のとおり、
 * スタッフ用ログインは `/staff` に分離した。
 */
export const PublicReservationPage: React.FC = () => {
  const [stores, setStores] = useState<PublicStore[]>([]);
  const [selectedStoreId, setSelectedStoreId] = useState<number | null>(null);
  const [view, setView] = useState<View>('loading');

  const [form, setForm] = useState<PublicReservationRequest>(EMPTY_FORM);
  const [result, setResult] = useState<PublicReservationResult | null>(null);

  const [messages, setMessages] = useState<string[]>([]);
  const [errorFields, setErrorFields] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    fetchPublicStores().then((storeList) => {
      if (cancelled) return;
      setStores(storeList);
      if (storeList.length === 0) {
        setView('no-store');
      } else if (storeList.length === 1) {
        setSelectedStoreId(storeList[0].id);
        setView('form');
      } else {
        setView('select-store');
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const selectStore = (storeId: number) => {
    setSelectedStoreId(storeId);
    setView('form');
  };

  const clearFieldError = (field: string) => {
    if (errorFields.includes(field)) {
      setErrorFields((prev) => prev.filter((f) => f !== field));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedStoreId === null) return;
    setMessages([]);
    setErrorFields([]);
    setSaving(true);
    try {
      const body: PublicReservationRequest = { ...form, reservedAt: `${form.reservedAt}:00` };
      const res = await createPublicReservation(selectedStoreId, body);
      if (res.ok) {
        setResult(res.data);
        setView('done');
        return;
      }
      setMessages(res.errors.map((err) => err.message));
      setErrorFields(Array.from(new Set(res.errors.flatMap((err) => err.fields))));
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

  if (view === 'loading') {
    return (
      <div style={{ maxWidth: '480px', margin: '40px auto', padding: '20px' }}>
        <p>読み込み中...</p>
      </div>
    );
  }

  if (view === 'no-store') {
    return (
      <div style={{ maxWidth: '480px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
        <h2>ご予約</h2>
        <p style={{ color: '#666' }}>現在、Web予約の受付は行っておりません。</p>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '480px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
      <h2>ご予約</h2>

      <TopMessage messages={messages} isError />

      {view === 'select-store' && (
        <div style={{ marginBottom: '15px' }}>
          <p style={{ color: '#666' }}>ご希望の店舗をお選びください。</p>
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

      {view === 'form' && (
        <form onSubmit={handleSubmit}>
          {stores.length > 1 && (
            <p style={{ color: '#666', marginBottom: '15px' }}>
              店舗: <strong>{stores.find((s) => s.id === selectedStoreId)?.name}</strong>{' '}
              <button
                type="button"
                onClick={() => setView('select-store')}
                style={{
                  background: 'none',
                  border: 'none',
                  color: '#007bff',
                  cursor: 'pointer',
                  fontSize: '13px',
                  textDecoration: 'underline',
                  padding: 0,
                }}
              >
                変更する
              </button>
            </p>
          )}
          <FormField label="ご来店日時">
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
          <FormField label="お名前">
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
          <FormField label="お電話番号">
            <input
              type="text"
              value={form.guestPhone}
              onChange={(e) => setForm((prev) => ({ ...prev, guestPhone: e.target.value }))}
              style={getInputStyle('guestPhone')}
            />
          </FormField>
          <FormField label="メールアドレス（任意。受付確認メールをお送りします）">
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
            {saving ? '送信中...' : 'この内容で予約する'}
          </button>
        </form>
      )}

      {view === 'done' && result !== null && (
        <div>
          <p style={{ color: '#198754', fontWeight: 'bold' }}>
            {result.status === 'CONFIRMED'
              ? 'ご予約が確定しました。'
              : 'ご予約を承りました。店舗からの確定のご連絡をお待ちください。'}
          </p>
          <div style={{ marginTop: '10px' }}>
            <div>お名前: {result.guestName} 様</div>
            <div>人数: {result.partySize}名</div>
          </div>
        </div>
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
