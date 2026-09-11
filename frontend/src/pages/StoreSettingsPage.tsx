import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TopMessage } from '../components/TopMessage';
import { fetchMe, type Me } from '../api/session';
import {
  fetchStores,
  createStore,
  fetchStoreSettings,
  updateStoreSettings,
  type Store,
  type StoreCreateRequest,
  type StoreSettingsRequest,
} from '../api/stores';

const TAX_ROUNDING_OPTIONS = [
  { value: 'FLOOR', label: '切り捨て' },
  { value: 'CEIL', label: '切り上げ' },
  { value: 'ROUND', label: '四捨五入' },
] as const;

const EMPTY_CREATE_FORM: StoreCreateRequest = {
  name: '',
  address: '',
  phone: '',
  businessHours: '',
  seatCount: 0,
};

const EMPTY_SETTINGS_FORM: StoreSettingsRequest = {
  name: '',
  address: '',
  phone: '',
  businessHours: '',
  seatCount: 0,
  taxRounding: 'FLOOR',
  priceIncludesTax: true,
  invoiceRegNo: '',
};

type View = 'list' | 'create' | 'edit';

/**
 * 店舗設定画面（FR-B01・FR-B03）。
 * 複数店舗を前提に、店舗が1件も無ければ最初の作成フォームを、1件以上あれば一覧を表示する。
 * 一覧からは既存店舗の編集と、新しい店舗の追加（経営管理者のみ）ができる（04_architecture.md §2.5）。
 */
export const StoreSettingsPage: React.FC = () => {
  const navigate = useNavigate();

  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);
  const [stores, setStores] = useState<Store[]>([]);
  const [view, setView] = useState<View>('list');
  const [selectedStoreId, setSelectedStoreId] = useState<number | null>(null);

  const [createForm, setCreateForm] = useState<StoreCreateRequest>(EMPTY_CREATE_FORM);
  const [settingsForm, setSettingsForm] = useState<StoreSettingsRequest>(EMPTY_SETTINGS_FORM);

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
      // 店舗が1件も無ければ、一覧を出さずいきなり最初の作成フォームを開く。
      setView(storeList.length === 0 ? 'create' : 'list');
      setLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [navigate]);

  const clearFieldError = (field: string) => {
    if (errorFields.includes(field)) {
      setErrorFields((prev) => prev.filter((f) => f !== field));
    }
  };

  const resetMessages = () => {
    setMessages([]);
    setErrorFields([]);
    setSuccessMessage('');
  };

  const openCreate = () => {
    resetMessages();
    setCreateForm(EMPTY_CREATE_FORM);
    setView('create');
  };

  const openEdit = async (storeId: number) => {
    resetMessages();
    const settings = await fetchStoreSettings(storeId);
    if (!settings) {
      setMessages(['店舗設定の取得に失敗しました。']);
      return;
    }
    setSelectedStoreId(storeId);
    setSettingsForm({
      name: settings.name,
      address: settings.address ?? '',
      phone: settings.phone ?? '',
      businessHours: settings.businessHours ?? '',
      seatCount: settings.seatCount,
      taxRounding: settings.taxRounding,
      priceIncludesTax: settings.priceIncludesTax,
      invoiceRegNo: settings.invoiceRegNo ?? '',
    });
    setView('edit');
  };

  const backToList = () => {
    resetMessages();
    setView('list');
  };

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    resetMessages();
    setSaving(true);

    try {
      const result = await createStore(createForm);
      if (result.ok) {
        const storeList = await fetchStores();
        setStores(storeList);
        setView('list');
        setSuccessMessage(`「${result.store.name}」を作成しました。`);
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

  const handleSettingsSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedStoreId === null) {
      return;
    }
    resetMessages();
    setSaving(true);

    try {
      const result = await updateStoreSettings(selectedStoreId, settingsForm);
      if (result.ok) {
        setStores((prev) =>
          prev.map((s) => (s.id === selectedStoreId ? { ...s, name: result.settings.name } : s))
        );
        setSuccessMessage('保存しました。');
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

  // 店舗が未作成で、かつ作成できない（経営管理者ではない）ロール。
  if (stores.length === 0 && me.role !== 'OWNER') {
    return (
      <div style={{ maxWidth: '480px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
        <h2>店舗設定</h2>
        <p style={{ color: '#666' }}>
          店舗がまだ作成されていません。経営管理者に店舗の作成を依頼してください。
        </p>
        <BackToHomeButton onClick={() => navigate('/home')} />
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '480px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
      <h2>店舗設定</h2>

      <TopMessage messages={messages} isError />
      <TopMessage messages={successMessage} isError={false} />

      {view === 'list' && (
        <>
          <div style={{ marginBottom: '15px' }}>
            {stores.map((store) => (
              <button
                key={store.id}
                type="button"
                onClick={() => openEdit(store.id)}
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
                <div style={{ fontWeight: 600 }}>{store.name}</div>
                {store.address && (
                  <div style={{ fontSize: '13px', color: '#666' }}>{store.address}</div>
                )}
              </button>
            ))}
          </div>

          {me.role === 'OWNER' && (
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
              ＋ 新しい店舗を追加
            </button>
          )}
        </>
      )}

      {view === 'create' && (
        <>
          {stores.length === 0 && (
            <p style={{ color: '#666' }}>まだ店舗が作成されていません。最初の店舗を作成してください。</p>
          )}
          <form onSubmit={handleCreateSubmit}>
            <FormField label="店舗名">
              <input
                type="text"
                value={createForm.name}
                onChange={(e) => {
                  setCreateForm((prev) => ({ ...prev, name: e.target.value }));
                  clearFieldError('name');
                }}
                required
                style={getInputStyle('name')}
              />
            </FormField>
            <FormField label="住所（任意）">
              <input
                type="text"
                value={createForm.address}
                onChange={(e) => setCreateForm((prev) => ({ ...prev, address: e.target.value }))}
                style={getInputStyle('address')}
              />
            </FormField>
            <FormField label="電話番号（任意）">
              <input
                type="tel"
                value={createForm.phone}
                onChange={(e) => setCreateForm((prev) => ({ ...prev, phone: e.target.value }))}
                style={getInputStyle('phone')}
              />
            </FormField>
            <FormField label="営業時間（任意）">
              <input
                type="text"
                placeholder="例: 17:00-24:00"
                value={createForm.businessHours}
                onChange={(e) => setCreateForm((prev) => ({ ...prev, businessHours: e.target.value }))}
                style={getInputStyle('businessHours')}
              />
            </FormField>
            <FormField label="席数">
              <input
                type="number"
                min={0}
                // 値が0のとき常に「0」を表示すると、消しても即座に0へ戻ってバックスペースで消せ
                // なくなる（続けて入力すると「02」のようになる）ため、0の間だけ空欄で表示する。
                value={createForm.seatCount === 0 ? '' : createForm.seatCount}
                onChange={(e) => {
                  const value = e.target.value === '' ? 0 : Number(e.target.value);
                  setCreateForm((prev) => ({ ...prev, seatCount: value }));
                  clearFieldError('seatCount');
                }}
                style={getInputStyle('seatCount')}
              />
            </FormField>
            <SubmitButton saving={saving} label="店舗を作成" />
          </form>
          {stores.length > 0 && <BackToListButton onClick={backToList} />}
        </>
      )}

      {view === 'edit' && (
        <>
          <form onSubmit={handleSettingsSubmit}>
            <FormField label="店舗名">
              <input
                type="text"
                value={settingsForm.name}
                onChange={(e) => {
                  setSettingsForm((prev) => ({ ...prev, name: e.target.value }));
                  clearFieldError('name');
                }}
                required
                style={getInputStyle('name')}
              />
            </FormField>
            <FormField label="住所（任意）">
              <input
                type="text"
                value={settingsForm.address}
                onChange={(e) => setSettingsForm((prev) => ({ ...prev, address: e.target.value }))}
                style={getInputStyle('address')}
              />
            </FormField>
            <FormField label="電話番号（任意）">
              <input
                type="tel"
                value={settingsForm.phone}
                onChange={(e) => setSettingsForm((prev) => ({ ...prev, phone: e.target.value }))}
                style={getInputStyle('phone')}
              />
            </FormField>
            <FormField label="営業時間（任意）">
              <input
                type="text"
                placeholder="例: 17:00-24:00"
                value={settingsForm.businessHours}
                onChange={(e) => setSettingsForm((prev) => ({ ...prev, businessHours: e.target.value }))}
                style={getInputStyle('businessHours')}
              />
            </FormField>
            <FormField label="席数">
              <input
                type="number"
                min={0}
                // 値が0のとき常に「0」を表示すると、消しても即座に0へ戻ってバックスペースで消せ
                // なくなる（続けて入力すると「02」のようになる）ため、0の間だけ空欄で表示する。
                value={settingsForm.seatCount === 0 ? '' : settingsForm.seatCount}
                onChange={(e) => {
                  const value = e.target.value === '' ? 0 : Number(e.target.value);
                  setSettingsForm((prev) => ({ ...prev, seatCount: value }));
                  clearFieldError('seatCount');
                }}
                style={getInputStyle('seatCount')}
              />
            </FormField>
            <FormField label="税額の端数処理">
              <select
                value={settingsForm.taxRounding}
                onChange={(e) => {
                  setSettingsForm((prev) => ({ ...prev, taxRounding: e.target.value }));
                  clearFieldError('taxRounding');
                }}
                style={getInputStyle('taxRounding')}
              >
                {TAX_ROUNDING_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </FormField>
            <div style={{ marginBottom: '15px' }}>
              <label>
                <input
                  type="checkbox"
                  checked={settingsForm.priceIncludesTax}
                  onChange={(e) =>
                    setSettingsForm((prev) => ({ ...prev, priceIncludesTax: e.target.checked }))
                  }
                  style={{ marginRight: '8px' }}
                />
                メニュー価格は税込で表示する
              </label>
            </div>
            <FormField label="適格請求書発行事業者登録番号（任意）">
              <input
                type="text"
                placeholder="例: T1234567890123"
                value={settingsForm.invoiceRegNo}
                onChange={(e) => setSettingsForm((prev) => ({ ...prev, invoiceRegNo: e.target.value }))}
                style={getInputStyle('invoiceRegNo')}
              />
            </FormField>
            <SubmitButton saving={saving} label="保存する" />
          </form>
          <BackToListButton onClick={backToList} />
        </>
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

const SubmitButton: React.FC<{ saving: boolean; label: string }> = ({ saving, label }) => (
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
    {saving ? '処理中...' : label}
  </button>
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
    ← 店舗一覧に戻る
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
