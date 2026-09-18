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
import { fetchTaxRates, createTaxRate, deleteTaxRate, type TaxRate, type TaxCategory } from '../api/taxRates';

const TAX_CATEGORY_LABELS: Record<TaxCategory, string> = {
  STANDARD_10: '標準税率',
  REDUCED_8: '軽減税率',
};

function todayStr(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

const TAX_ROUNDING_OPTIONS = [
  { value: 'FLOOR', label: '切り捨て' },
  { value: 'CEIL', label: '切り上げ' },
  { value: 'ROUND', label: '四捨五入' },
] as const;

const WEB_RESERVATION_MODE_OPTIONS = [
  { value: 'APPROVAL', label: '承認制（店舗が確定操作をするまで未確定）' },
  { value: 'INSTANT', label: '即時確定（申込と同時に確定）' },
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
  webReservationMode: 'APPROVAL',
  cancelChargeDefaultCustomer: true,
  cancelChargeDefaultStore: false,
  requireManagerApprovalForServeCancel: false,
  requireManagerApprovalForVoidRefund: false,
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

  const [taxRates, setTaxRates] = useState<TaxRate[]>([]);
  const [newRateCategory, setNewRateCategory] = useState<TaxCategory>('STANDARD_10');
  const [newRatePercent, setNewRatePercent] = useState(10);
  const [newRateEffectiveFrom, setNewRateEffectiveFrom] = useState(todayStr());
  const [taxRateSaving, setTaxRateSaving] = useState(false);
  const [taxRateMessage, setTaxRateMessage] = useState('');

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
        navigate('/staff', { replace: true });
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
      webReservationMode: settings.webReservationMode,
      cancelChargeDefaultCustomer: settings.cancelChargeDefaultCustomer,
      cancelChargeDefaultStore: settings.cancelChargeDefaultStore,
      requireManagerApprovalForServeCancel: settings.requireManagerApprovalForServeCancel,
      requireManagerApprovalForVoidRefund: settings.requireManagerApprovalForVoidRefund,
    });
    setTaxRates(await fetchTaxRates(storeId));
    setNewRateCategory('STANDARD_10');
    setNewRatePercent(10);
    setNewRateEffectiveFrom(todayStr());
    setTaxRateMessage('');
    setView('edit');
  };

  const submitNewTaxRate = async () => {
    if (selectedStoreId === null) return;
    setTaxRateMessage('');
    setTaxRateSaving(true);
    try {
      const result = await createTaxRate(selectedStoreId, {
        taxCategory: newRateCategory,
        ratePercent: newRatePercent,
        effectiveFrom: newRateEffectiveFrom,
      });
      if (!result.ok) {
        setTaxRateMessage(result.errors.map((err) => err.message).join(' '));
        return;
      }
      setTaxRates(await fetchTaxRates(selectedStoreId));
      setNewRatePercent(10);
    } catch (error) {
      console.error('通信エラー:', error);
      setTaxRateMessage('サーバーとの通信に失敗しました。');
    } finally {
      setTaxRateSaving(false);
    }
  };

  const handleDeleteTaxRate = async (rateId: number) => {
    if (selectedStoreId === null) return;
    setTaxRateMessage('');
    const result = await deleteTaxRate(selectedStoreId, rateId);
    if (!result.ok) {
      setTaxRateMessage(result.errors.map((err) => err.message).join(' '));
      return;
    }
    setTaxRates(await fetchTaxRates(selectedStoreId));
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
            <SectionHeading>税金設定</SectionHeading>
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

            <SectionHeading>税率</SectionHeading>
            <p style={{ color: '#666', fontSize: '13px', marginTop: '-8px', marginBottom: '12px' }}>
              「標準税率」「軽減税率」という区分は変わりません。実際のパーセンテージを、
              いつから適用するかとあわせて登録します。終了日は無く、次の税率の適用開始日の
              前日までが自動的にその税率の期間になります。
            </p>
            {(['STANDARD_10', 'REDUCED_8'] as const).map((category) => {
              const history = taxRates.filter((r) => r.taxCategory === category);
              return (
                <div key={category} style={{ marginBottom: '12px' }}>
                  <div style={{ fontWeight: 600, marginBottom: '4px' }}>{TAX_CATEGORY_LABELS[category]}</div>
                  {history.length === 0 && (
                    <div style={{ fontSize: '13px', color: '#666' }}>
                      未設定（既定の{category === 'STANDARD_10' ? '10' : '8'}%を適用中）
                    </div>
                  )}
                  {history.map((rate) => (
                    <div
                      key={rate.id}
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        fontSize: '13px',
                        padding: '4px 0',
                      }}
                    >
                      <span style={{ color: rate.currentlyEffective ? '#000' : '#666' }}>
                        {rate.ratePercent}%（{rate.effectiveFrom}〜{rate.currentlyEffective ? '・適用中' : ''}）
                      </span>
                      {!rate.currentlyEffective && new Date(rate.effectiveFrom) > new Date(todayStr()) && (
                        <button
                          type="button"
                          onClick={() => handleDeleteTaxRate(rate.id)}
                          style={{
                            padding: '4px 10px',
                            fontSize: '12px',
                            backgroundColor: '#fff',
                            color: '#dc3545',
                            border: '1px solid #dc3545',
                            borderRadius: '4px',
                            cursor: 'pointer',
                          }}
                        >
                          削除
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              );
            })}
            <div style={{ border: '1px solid #ccc', borderRadius: '4px', padding: '10px 12px', marginBottom: '15px' }}>
              <div style={{ display: 'flex', gap: '10px', marginBottom: '10px' }}>
                <select
                  value={newRateCategory}
                  onChange={(e) => setNewRateCategory(e.target.value as TaxCategory)}
                  style={{ flex: 1, padding: '8px' }}
                >
                  <option value="STANDARD_10">標準税率</option>
                  <option value="REDUCED_8">軽減税率</option>
                </select>
                <input
                  type="number"
                  step="0.01"
                  value={newRatePercent}
                  onChange={(e) => setNewRatePercent(Number(e.target.value))}
                  style={{ flex: 1, padding: '8px', boxSizing: 'border-box' }}
                  placeholder="税率（%）"
                />
                <input
                  type="date"
                  value={newRateEffectiveFrom}
                  onChange={(e) => setNewRateEffectiveFrom(e.target.value)}
                  style={{ flex: 1, padding: '8px', boxSizing: 'border-box' }}
                />
              </div>
              {taxRateMessage && (
                <p style={{ color: '#dc3545', fontSize: '13px', margin: '0 0 8px' }}>{taxRateMessage}</p>
              )}
              <button
                type="button"
                onClick={submitNewTaxRate}
                disabled={taxRateSaving}
                style={{
                  width: '100%',
                  padding: '8px',
                  backgroundColor: '#fff',
                  color: '#007bff',
                  border: '1px dashed #007bff',
                  borderRadius: '4px',
                  cursor: 'pointer',
                }}
              >
                {taxRateSaving ? '処理中...' : '＋ 税率の変更を追加'}
              </button>
            </div>

            <SectionHeading>予約・キャンセルのルール</SectionHeading>
            <FormField label="Web予約の確定方式">
              <select
                value={settingsForm.webReservationMode}
                onChange={(e) => {
                  setSettingsForm((prev) => ({ ...prev, webReservationMode: e.target.value }));
                  clearFieldError('webReservationMode');
                }}
                style={getInputStyle('webReservationMode')}
              >
                {WEB_RESERVATION_MODE_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="オーダー提供後に取消・キャンセルしたオーダーの請求について">
              <div
                style={{
                  border: '1px solid #ccc',
                  borderRadius: '4px',
                  padding: '10px 12px',
                }}
              >
                <label style={{ display: 'block', marginBottom: '6px' }}>
                  <input
                    type="checkbox"
                    checked={settingsForm.cancelChargeDefaultCustomer}
                    onChange={(e) =>
                      setSettingsForm((prev) => ({
                        ...prev,
                        cancelChargeDefaultCustomer: e.target.checked,
                      }))
                    }
                    style={{ marginRight: '8px' }}
                  />
                  お客様都合のキャンセルはデフォルトで請求する
                </label>
                <label style={{ display: 'block' }}>
                  <input
                    type="checkbox"
                    checked={settingsForm.cancelChargeDefaultStore}
                    onChange={(e) =>
                      setSettingsForm((prev) => ({ ...prev, cancelChargeDefaultStore: e.target.checked }))
                    }
                    style={{ marginRight: '8px' }}
                  />
                  店舗都合キャンセルはデフォルトで請求する
                </label>
                <p style={{ color: '#666', fontSize: '13px', margin: '8px 0 0' }}>
                  ※但し、会計時にスタッフが個別のオーダーに対して請求する・請求しないを変更することができます。
                </p>
              </div>
            </FormField>
            <FormField label="提供後の注文取消">
              <label style={{ display: 'block' }}>
                <input
                  type="checkbox"
                  checked={settingsForm.requireManagerApprovalForServeCancel}
                  onChange={(e) =>
                    setSettingsForm((prev) => ({
                      ...prev,
                      requireManagerApprovalForServeCancel: e.target.checked,
                    }))
                  }
                  style={{ marginRight: '8px' }}
                />
                提供済みの品の取消は店長・経営管理者のみ行える（要店長承認）
              </label>
              <p style={{ color: '#666', fontSize: '13px', margin: '8px 0 0' }}>
                ※チェックを外すと、ホールスタッフも提供済みの品を取消できます（既定）。提供前の取消はこの設定に関わらず行えます。
              </p>
            </FormField>
            <FormField label="会計の取消・返金・値引き">
              <label style={{ display: 'block' }}>
                <input
                  type="checkbox"
                  checked={settingsForm.requireManagerApprovalForVoidRefund}
                  onChange={(e) =>
                    setSettingsForm((prev) => ({
                      ...prev,
                      requireManagerApprovalForVoidRefund: e.target.checked,
                    }))
                  }
                  style={{ marginRight: '8px' }}
                />
                会計の取消・返金・値引きは店長・経営管理者のみ行える（要店長承認）
              </label>
              <p style={{ color: '#666', fontSize: '13px', margin: '8px 0 0' }}>
                ※チェックを外すと、ホールスタッフも会計の取消・返金・値引きができます（既定）。
              </p>
            </FormField>
            <SubmitButton saving={saving} label="保存する" />
          </form>
          <BackToListButton onClick={backToList} />
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

/** フォーム内の項目をグループ分けする見出し。項目の境界が分かりにくくなるのを防ぐ。 */
const SectionHeading: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <h3
    style={{
      fontSize: '14px',
      fontWeight: 700,
      color: '#333',
      margin: '24px 0 12px',
      paddingTop: '16px',
      borderTop: '1px solid #ddd',
    }}
  >
    {children}
  </h3>
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
