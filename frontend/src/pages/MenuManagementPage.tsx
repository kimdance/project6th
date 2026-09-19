import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TopMessage } from '../components/TopMessage';
import { fetchMe, type Me } from '../api/session';
import { fetchStores, type Store } from '../api/stores';
import {
  fetchMenuCategories,
  createMenuCategory,
  updateMenuCategory,
  fetchMenuItems,
  createMenuItem,
  updateMenuItem,
  updateMenuItemSalesStatus,
  uploadMenuItemPhoto,
  toPhotoDisplayUrl,
  type MenuCategory,
  type MenuCategoryRequest,
  type MenuItem,
  type MenuItemRequest,
  type TaxCategory,
  type PrepType,
  type SalesStatus,
} from '../api/menu';

/**
 * バックエンドの spring.servlet.multipart.max-file-size（50MB）と同じ上限。サーバー側の
 * チェックだけに頼らず、送信前にここで弾く。理由は二つ：(1) 上限超は不正なファイルとして
 * サーバー側マルチパート解析の時点（Spring MVCのハンドラに到達する前）で例外になり、
 * クライアントがボディを送り切る前にサーバーが応答を返そうとする形になる。開発機がWSL2の
 * 場合、この「クライアントが送信中にサーバーが先に応答する」パターンはWindows→WSL2の
 * localhostポートフォワーディング中継（wslrelay）がうまく扱えず、リクエストがハングして
 * タイムアウトすることを確認した（docs/ops/dev-machine-setup.md）。(2) 素直に無駄な
 * アップロード帯域・時間を避けられる。
 */
const MAX_PHOTO_SIZE_BYTES = 50 * 1024 * 1024;
const PHOTO_TOO_LARGE_MESSAGE = '写真ファイルが大きすぎます（50MBまでです）。';

const TAX_CATEGORY_LABELS: Record<TaxCategory, string> = {
  STANDARD_10: '標準10%',
  REDUCED_8: '軽減税率8%',
};

const PREP_TYPE_LABELS: Record<PrepType, string> = {
  COOK: '調理あり',
  NO_COOK: '調理なし',
};

const SALES_STATUS_LABELS: Record<SalesStatus, string> = {
  ON_SALE: '販売中',
  SOLD_OUT: '売り切れ',
  SUSPENDED: '提供停止',
};

const SALES_STATUS_COLORS: Record<SalesStatus, string> = {
  ON_SALE: '#28a745',
  SOLD_OUT: '#dc3545',
  SUSPENDED: '#6c757d',
};

const EMPTY_CATEGORY_FORM: MenuCategoryRequest = { name: '', displayOrder: 0, active: true };

const emptyItemForm = (categoryId: number): MenuItemRequest => ({
  categoryId,
  name: '',
  description: '',
  priceJpy: 0,
  taxCategory: 'STANDARD_10',
  prepType: 'COOK',
  photoUrl: '',
  serveTimeFrom: null,
  serveTimeTo: null,
  displayOrder: 0,
  active: true,
  salesStatus: 'ON_SALE',
});

type Tab = 'items' | 'categories';
type View = 'select-store' | 'list' | 'form';
type ActiveFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';

const ACTIVE_FILTER_LABELS: Record<ActiveFilter, string> = {
  ALL: 'すべて',
  ACTIVE: '有効のみ',
  INACTIVE: '無効のみ',
};

const matchesActiveFilter = (filter: ActiveFilter, active: boolean) =>
  filter === 'ALL' || (filter === 'ACTIVE') === active;

const cameraSupported = typeof navigator !== 'undefined' && !!navigator.mediaDevices?.getUserMedia;

/**
 * メニュー管理画面（FR-D01〜D03）。
 * 店舗が複数あれば先に店舗を選ばせ、選んだ店舗のカテゴリ・メニュー項目を管理する。
 * フルの編集（登録・価格変更等）は経営管理者・店長のみ、売り切れ・提供停止の切替は
 * ホール・キッチンも行える（MenuService・StoreAccessGuard）。
 * 期間限定メニュー（FR-D04）とトッピング等の簡易オプション（FR-D05）はフェーズ1未実装。
 */
export const MenuManagementPage: React.FC = () => {
  const navigate = useNavigate();

  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);
  const [stores, setStores] = useState<Store[]>([]);
  const [selectedStoreId, setSelectedStoreId] = useState<number | null>(null);
  const [view, setView] = useState<View>('list');
  const [tab, setTab] = useState<Tab>('items');

  const [categories, setCategories] = useState<MenuCategory[]>([]);
  const [items, setItems] = useState<MenuItem[]>([]);

  const [editingCategoryId, setEditingCategoryId] = useState<number | null>(null);
  const [categoryForm, setCategoryForm] = useState<MenuCategoryRequest>(EMPTY_CATEGORY_FORM);

  const [editingItemId, setEditingItemId] = useState<number | null>(null);
  const [editingItem, setEditingItem] = useState<MenuItem | null>(null);
  const [itemForm, setItemForm] = useState<MenuItemRequest>(emptyItemForm(0));

  const [messages, setMessages] = useState<string[]>([]);
  const [errorFields, setErrorFields] = useState<string[]>([]);
  const [successMessage, setSuccessMessage] = useState('');
  // 販売状況の切替（一覧・編集画面どちらのボタンからでも起こりうる）のエラーは、対象のメニュー
  // 項目自身のカード／欄に出したいため、ページ共通の messages とは別に商品IDごとに持つ。
  const [itemErrors, setItemErrors] = useState<Record<number, string[]>>({});
  const [saving, setSaving] = useState(false);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  const [cameraStream, setCameraStream] = useState<MediaStream | null>(null);
  const [cameraError, setCameraError] = useState('');
  const videoRef = useRef<HTMLVideoElement>(null);

  const [categoryActiveFilter, setCategoryActiveFilter] = useState<ActiveFilter>('ALL');
  const [itemActiveFilter, setItemActiveFilter] = useState<ActiveFilter>('ALL');
  const [itemCategoryFilter, setItemCategoryFilter] = useState<number | 'ALL'>('ALL');
  const [itemSalesStatusFilter, setItemSalesStatusFilter] = useState<SalesStatus | 'ALL'>('ALL');

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

      if (storeList.length === 1) {
        await selectStoreData(storeList[0].id);
        setView('list');
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

  const cameraStreamRef = useRef<MediaStream | null>(null);
  useEffect(() => {
    cameraStreamRef.current = cameraStream;
    if (videoRef.current) {
      videoRef.current.srcObject = cameraStream;
    }
  }, [cameraStream]);

  useEffect(() => {
    // 画面を離れる際にカメラを掴んだままにしない（他アプリでの利用を妨げないため）。
    return () => {
      cameraStreamRef.current?.getTracks().forEach((track) => track.stop());
    };
  }, []);

  const selectStoreData = async (storeId: number) => {
    setSelectedStoreId(storeId);
    const [categoryList, itemList] = await Promise.all([fetchMenuCategories(storeId), fetchMenuItems(storeId)]);
    setCategories(categoryList);
    setItems(itemList);
    // 別の店舗のカテゴリIDを引きずらないよう、店舗を切り替えるたびに絞り込み条件をリセットする。
    setCategoryActiveFilter('ALL');
    setItemActiveFilter('ALL');
    setItemCategoryFilter('ALL');
    setItemSalesStatusFilter('ALL');
  };

  const selectStore = async (storeId: number) => {
    await selectStoreData(storeId);
    setTab('items');
    setView('list');
  };

  const canEditFull = (storeId: number) =>
    !!me && (me.role === 'OWNER' || (me.role === 'MANAGER' && me.stores.some((s) => s.id === storeId)));

  const canToggleStatus = (storeId: number) =>
    canEditFull(storeId) ||
    (!!me && (me.role === 'HALL' || me.role === 'KITCHEN') && me.stores.some((s) => s.id === storeId));

  const resetMessages = () => {
    setMessages([]);
    setErrorFields([]);
    setSuccessMessage('');
  };

  const clearFieldError = (field: string) => {
    if (errorFields.includes(field)) {
      setErrorFields((prev) => prev.filter((f) => f !== field));
    }
  };

  // ---- カテゴリ ----

  const openCreateCategory = () => {
    resetMessages();
    setEditingCategoryId(null);
    setCategoryForm(EMPTY_CATEGORY_FORM);
    setTab('categories');
    setView('form');
  };

  const openEditCategory = (category: MenuCategory) => {
    resetMessages();
    setEditingCategoryId(category.id);
    setCategoryForm({ name: category.name, displayOrder: category.displayOrder, active: category.active });
    setTab('categories');
    setView('form');
  };

  const handleCategorySubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedStoreId === null) {
      return;
    }
    resetMessages();
    setSaving(true);
    try {
      const result =
        editingCategoryId === null
          ? await createMenuCategory(selectedStoreId, categoryForm)
          : await updateMenuCategory(selectedStoreId, editingCategoryId, categoryForm);

      if (result.ok) {
        const [categoryList, itemList] = await Promise.all([
          fetchMenuCategories(selectedStoreId),
          // カテゴリを無効化すると配下のメニュー項目が一括で「提供停止」になるため、
          // 一覧側の表示も合わせて更新する。
          fetchMenuItems(selectedStoreId),
        ]);
        setCategories(categoryList);
        setItems(itemList);
        setView('list');
        setSuccessMessage(`「${result.category.name}」を保存しました。`);
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

  // ---- メニュー項目 ----

  const openCreateItem = () => {
    stopCamera();
    resetMessages();
    setEditingItemId(null);
    setEditingItem(null);
    setItemForm(emptyItemForm(categories[0]?.id ?? 0));
    setTab('items');
    setView('form');
  };

  const openEditItem = (item: MenuItem) => {
    stopCamera();
    resetMessages();
    // 一覧側で起きた過去の失敗が、何も操作していない詳細画面にそのまま出てしまわないよう、
    // 詳細画面を開くたびにこの項目のエラー表示はリセットする（この画面で改めて操作して
    // 失敗した場合にのみ、toggleStatus 側で再度セットされる）。
    setItemErrors((prev) => {
      if (!(item.id in prev)) {
        return prev;
      }
      const next = { ...prev };
      delete next[item.id];
      return next;
    });
    setEditingItemId(item.id);
    setEditingItem(item);
    setItemForm({
      categoryId: item.categoryId,
      name: item.name,
      description: item.description ?? '',
      priceJpy: item.priceJpy,
      taxCategory: item.taxCategory,
      prepType: item.prepType,
      photoUrl: item.photoUrl ?? '',
      serveTimeFrom: item.serveTimeFrom ? item.serveTimeFrom.slice(0, 5) : null,
      serveTimeTo: item.serveTimeTo ? item.serveTimeTo.slice(0, 5) : null,
      displayOrder: item.displayOrder,
      active: item.active,
      salesStatus: item.salesStatus,
    });
    setTab('items');
    setView('form');
  };

  const handleItemSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedStoreId === null) {
      return;
    }
    stopCamera();
    resetMessages();
    setSaving(true);
    try {
      const result =
        editingItemId === null
          ? await createMenuItem(selectedStoreId, itemForm)
          : await updateMenuItem(selectedStoreId, editingItemId, itemForm);

      if (result.ok) {
        setItems(await fetchMenuItems(selectedStoreId));
        setView('list');
        setSuccessMessage(`「${result.item.name}」を保存しました。`);
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

  const uploadPhotoFile = async (file: File) => {
    if (selectedStoreId === null) {
      return;
    }
    resetMessages();
    if (file.size > MAX_PHOTO_SIZE_BYTES) {
      setMessages([PHOTO_TOO_LARGE_MESSAGE]);
      return;
    }
    setUploadingPhoto(true);
    try {
      const result = await uploadMenuItemPhoto(selectedStoreId, file);
      if (result.ok) {
        setItemForm((prev) => ({ ...prev, photoUrl: result.photoUrl }));
      } else {
        setMessages(result.errors.map((err) => err.message));
      }
    } catch (error) {
      console.error('通信エラー:', error);
      setMessages(['写真のアップロードに失敗しました。']);
    } finally {
      setUploadingPhoto(false);
    }
  };

  const handlePhotoFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) {
      return;
    }
    await uploadPhotoFile(file);
  };

  const startCamera = async () => {
    resetMessages();
    setCameraError('');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
      setCameraStream(stream);
    } catch (error) {
      console.error('カメラの起動に失敗:', error);
      setCameraError('カメラを起動できませんでした。ブラウザのカメラ権限設定をご確認ください。');
    }
  };

  const stopCamera = () => {
    cameraStream?.getTracks().forEach((track) => track.stop());
    setCameraStream(null);
  };

  const capturePhoto = async () => {
    const video = videoRef.current;
    if (!video || video.videoWidth === 0) {
      return;
    }
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return;
    }
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    const blob: Blob | null = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.9));
    stopCamera();
    if (!blob) {
      setMessages(['写真の撮影に失敗しました。']);
      return;
    }
    await uploadPhotoFile(new File([blob], `camera-${Date.now()}.jpg`, { type: 'image/jpeg' }));
  };

  const clearPhoto = () => {
    setItemForm((prev) => ({ ...prev, photoUrl: '' }));
  };

  const toggleStatus = async (item: MenuItem, nextStatus: SalesStatus) => {
    if (selectedStoreId === null) {
      return;
    }
    resetMessages();
    setItemErrors((prev) => {
      if (!(item.id in prev)) {
        return prev;
      }
      const next = { ...prev };
      delete next[item.id];
      return next;
    });
    const result = await updateMenuItemSalesStatus(selectedStoreId, item.id, nextStatus);
    if (result.ok) {
      setItems((prev) => prev.map((i) => (i.id === item.id ? result.item : i)));
      setEditingItem((prev) => (prev && prev.id === item.id ? result.item : prev));
      setSuccessMessage(`「${result.item.name}」を${SALES_STATUS_LABELS[nextStatus]}にしました。`);
    } else {
      setItemErrors((prev) => ({ ...prev, [item.id]: result.errors.map((err) => err.message) }));
    }
  };

  const backToList = () => {
    stopCamera();
    resetMessages();
    setView('list');
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
        <h2>メニュー管理</h2>
        <p style={{ color: '#666' }}>先に店舗設定から店舗を作成してください。</p>
      </div>
    );
  }

  const editable = selectedStoreId !== null && canEditFull(selectedStoreId);
  const toggleable = selectedStoreId !== null && canToggleStatus(selectedStoreId);

  const categoryById = new Map(categories.map((c) => [c.id, c]));
  // フォーム未保存の入力値(itemForm)を基準に、販売状況を「提供停止」以外へ変更してよいか判定する
  // （項目・カテゴリのどちらかが無効なら不可。MenuService#updateItemの検証と一致させる）。
  const canEnableSalesStatus = itemForm.active && categoryById.get(itemForm.categoryId)?.active !== false;
  const filteredCategories = categories.filter((c) => matchesActiveFilter(categoryActiveFilter, c.active));
  const filteredItems = items
    .filter(
      (i) =>
        matchesActiveFilter(itemActiveFilter, i.active) &&
        (itemCategoryFilter === 'ALL' || i.categoryId === itemCategoryFilter) &&
        (itemSalesStatusFilter === 'ALL' || i.salesStatus === itemSalesStatusFilter)
    )
    // カテゴリの並び順を優先し、同じカテゴリ内はメニュー項目自身の並び順に従う。
    .sort((a, b) => {
      const categoryOrderDiff =
        (categoryById.get(a.categoryId)?.displayOrder ?? 0) - (categoryById.get(b.categoryId)?.displayOrder ?? 0);
      if (categoryOrderDiff !== 0) {
        return categoryOrderDiff;
      }
      return a.displayOrder - b.displayOrder || a.id - b.id;
    });

  return (
    <div style={{ maxWidth: '560px', margin: '40px auto', padding: '20px', textAlign: 'left' }}>
      <h2>メニュー管理</h2>

      <TopMessage messages={messages} isError />
      <TopMessage messages={successMessage} isError={false} />

      {view === 'select-store' && (
        <div style={{ marginBottom: '15px' }}>
          <p style={{ color: '#666' }}>メニューを管理する店舗を選んでください。</p>
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

      {view !== 'select-store' && selectedStoreId !== null && (
        <>
          {stores.length > 1 && view === 'list' && (
            <p style={{ color: '#666', marginBottom: '15px' }}>
              店舗: <strong>{stores.find((s) => s.id === selectedStoreId)?.name}</strong>
            </p>
          )}

          {view === 'list' && (
            <div style={{ display: 'flex', gap: '8px', marginBottom: '15px', borderBottom: '1px solid #ddd' }}>
              <TabButton label="メニュー項目" active={tab === 'items'} onClick={() => setTab('items')} />
              <TabButton label="カテゴリ" active={tab === 'categories'} onClick={() => setTab('categories')} />
            </div>
          )}

          {view === 'list' && tab === 'items' && (
            <>
              <div style={{ display: 'flex', gap: '10px', marginBottom: '15px', flexWrap: 'wrap' }}>
                <label style={{ fontSize: '13px' }}>
                  状態:{' '}
                  <select
                    value={itemActiveFilter}
                    onChange={(e) => setItemActiveFilter(e.target.value as ActiveFilter)}
                  >
                    {(Object.keys(ACTIVE_FILTER_LABELS) as ActiveFilter[]).map((f) => (
                      <option key={f} value={f}>
                        {ACTIVE_FILTER_LABELS[f]}
                      </option>
                    ))}
                  </select>
                </label>
                <label style={{ fontSize: '13px' }}>
                  カテゴリ:{' '}
                  <select
                    value={itemCategoryFilter}
                    onChange={(e) =>
                      setItemCategoryFilter(e.target.value === 'ALL' ? 'ALL' : Number(e.target.value))
                    }
                  >
                    <option value="ALL">すべて</option>
                    {categories.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label style={{ fontSize: '13px' }}>
                  販売状況:{' '}
                  <select
                    value={itemSalesStatusFilter}
                    onChange={(e) =>
                      setItemSalesStatusFilter(e.target.value as SalesStatus | 'ALL')
                    }
                  >
                    <option value="ALL">すべて</option>
                    {(Object.keys(SALES_STATUS_LABELS) as SalesStatus[]).map((s) => (
                      <option key={s} value={s}>
                        {SALES_STATUS_LABELS[s]}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <ItemList
                items={filteredItems}
                categoryById={categoryById}
                itemErrors={itemErrors}
                editable={editable}
                toggleable={toggleable}
                onEdit={openEditItem}
                onToggle={toggleStatus}
                onCreate={openCreateItem}
                canCreate={editable && categories.length > 0}
              />
            </>
          )}

          {view === 'list' && tab === 'items' && editable && categories.length === 0 && (
            <p style={{ color: '#666', marginBottom: '15px' }}>
              先に「カテゴリ」タブからカテゴリを1件作成してください。
            </p>
          )}

          {view === 'list' && tab === 'categories' && (
            <>
              <div style={{ marginBottom: '15px' }}>
                <label style={{ fontSize: '13px' }}>
                  状態:{' '}
                  <select
                    value={categoryActiveFilter}
                    onChange={(e) => setCategoryActiveFilter(e.target.value as ActiveFilter)}
                  >
                    {(Object.keys(ACTIVE_FILTER_LABELS) as ActiveFilter[]).map((f) => (
                      <option key={f} value={f}>
                        {ACTIVE_FILTER_LABELS[f]}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <CategoryList
                categories={filteredCategories}
                editable={editable}
                onEdit={openEditCategory}
                onCreate={openCreateCategory}
              />
            </>
          )}

          {view === 'list' && stores.length > 1 && (
            <button
              type="button"
              onClick={() => {
                resetMessages();
                setView('select-store');
              }}
              style={secondaryButtonStyle}
            >
              ← 店舗を選び直す
            </button>
          )}

          {view === 'form' && tab === 'categories' && (
            <form onSubmit={handleCategorySubmit}>
              <FormField label="カテゴリ名">
                <input
                  type="text"
                  value={categoryForm.name}
                  onChange={(e) => {
                    setCategoryForm((prev) => ({ ...prev, name: e.target.value }));
                    clearFieldError('name');
                  }}
                  required
                  style={getInputStyle('name')}
                />
              </FormField>
              <FormField label="並び順（小さいほど先に表示）">
                <input
                  type="number"
                  value={categoryForm.displayOrder}
                  onChange={(e) =>
                    setCategoryForm((prev) => ({ ...prev, displayOrder: Number(e.target.value) || 0 }))
                  }
                  style={getInputStyle('displayOrder')}
                />
              </FormField>
              <div style={{ marginBottom: '15px' }}>
                <label>
                  <input
                    type="checkbox"
                    checked={categoryForm.active}
                    onChange={(e) => setCategoryForm((prev) => ({ ...prev, active: e.target.checked }))}
                    style={{ marginRight: '8px' }}
                  />
                  有効にする
                </label>
              </div>
              <button type="submit" disabled={saving} style={primaryButtonStyle}>
                {saving ? '処理中...' : '保存する'}
              </button>
              <BackToListButton onClick={backToList} />
            </form>
          )}

          {view === 'form' && tab === 'items' && (
            <form onSubmit={handleItemSubmit}>
              {editingItem && (
                <FormField label="販売状況">
                  <div style={{ marginTop: '5px', display: 'flex', gap: '16px' }}>
                    {(['ON_SALE', 'SOLD_OUT', 'SUSPENDED'] as const).map((s) => {
                      const disabled = s !== 'SUSPENDED' && !canEnableSalesStatus;
                      return (
                        <label
                          key={s}
                          style={{ display: 'flex', alignItems: 'center', gap: '4px', opacity: disabled ? 0.5 : 1 }}
                        >
                          <input
                            type="radio"
                            name="salesStatus"
                            checked={itemForm.salesStatus === s}
                            disabled={disabled}
                            onChange={() => setItemForm((prev) => ({ ...prev, salesStatus: s }))}
                          />
                          {SALES_STATUS_LABELS[s]}
                        </label>
                      );
                    })}
                  </div>
                  {!canEnableSalesStatus && (
                    <p style={{ color: '#dc3545', fontSize: '13px', marginTop: '4px' }}>
                      メニューが無効の場合、設定する販売状況は「提供停止」にしてください。
                    </p>
                  )}
                </FormField>
              )}
              <FormField label="カテゴリ">
                <select
                  value={itemForm.categoryId}
                  onChange={(e) => {
                    const categoryId = Number(e.target.value);
                    const categoryActive = categoryById.get(categoryId)?.active !== false;
                    setItemForm((prev) => ({
                      ...prev,
                      categoryId,
                      salesStatus: categoryActive ? prev.salesStatus : 'SUSPENDED',
                    }));
                    clearFieldError('categoryId');
                  }}
                  style={getInputStyle('categoryId')}
                >
                  {categories.length === 0 && <option value={0}>（カテゴリがありません）</option>}
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </FormField>
              <FormField label="メニュー名">
                <input
                  type="text"
                  value={itemForm.name}
                  onChange={(e) => {
                    setItemForm((prev) => ({ ...prev, name: e.target.value }));
                    clearFieldError('name');
                  }}
                  required
                  style={getInputStyle('name')}
                />
              </FormField>
              <FormField label="説明（任意）">
                <textarea
                  value={itemForm.description}
                  onChange={(e) => setItemForm((prev) => ({ ...prev, description: e.target.value }))}
                  rows={3}
                  style={{ ...getInputStyle('description'), resize: 'vertical' }}
                />
              </FormField>
              <FormField label="価格（円・税抜/税込は税区分に従う）">
                <input
                  type="number"
                  min={0}
                  value={itemForm.priceJpy === 0 ? '' : itemForm.priceJpy}
                  onChange={(e) => {
                    setItemForm((prev) => ({ ...prev, priceJpy: e.target.value === '' ? 0 : Number(e.target.value) }));
                    clearFieldError('priceJpy');
                  }}
                  style={getInputStyle('priceJpy')}
                />
              </FormField>
              <FormField label="税区分">
                <div style={{ marginTop: '5px', display: 'flex', gap: '16px' }}>
                  {(['STANDARD_10', 'REDUCED_8'] as const).map((tc) => (
                    <label key={tc} style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                      <input
                        type="radio"
                        name="taxCategory"
                        checked={itemForm.taxCategory === tc}
                        onChange={() => {
                          setItemForm((prev) => ({ ...prev, taxCategory: tc }));
                          clearFieldError('taxCategory');
                        }}
                      />
                      {TAX_CATEGORY_LABELS[tc]}
                    </label>
                  ))}
                </div>
              </FormField>
              <FormField label="調理区分">
                <div style={{ marginTop: '5px', display: 'flex', gap: '16px' }}>
                  {(['COOK', 'NO_COOK'] as const).map((pt) => (
                    <label key={pt} style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                      <input
                        type="radio"
                        name="prepType"
                        checked={itemForm.prepType === pt}
                        onChange={() => setItemForm((prev) => ({ ...prev, prepType: pt }))}
                      />
                      {PREP_TYPE_LABELS[pt]}
                    </label>
                  ))}
                </div>
              </FormField>
              <FormField label="提供時間帯（任意。指定する場合は開始・終了の両方）">
                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                  <input
                    type="time"
                    value={itemForm.serveTimeFrom ?? ''}
                    onChange={(e) => {
                      setItemForm((prev) => ({ ...prev, serveTimeFrom: e.target.value || null }));
                      clearFieldError('serveTimeFrom');
                    }}
                    style={{ ...getInputStyle('serveTimeFrom'), width: 'auto' }}
                  />
                  <span>〜</span>
                  <input
                    type="time"
                    value={itemForm.serveTimeTo ?? ''}
                    onChange={(e) => setItemForm((prev) => ({ ...prev, serveTimeTo: e.target.value || null }))}
                    style={{ ...getInputStyle('serveTimeFrom'), width: 'auto' }}
                  />
                </div>
              </FormField>
              <FormField label="写真（任意）">
                <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    capture="environment"
                    onChange={handlePhotoFileChange}
                    disabled={uploadingPhoto || cameraStream !== null}
                  />
                  {cameraSupported && cameraStream === null && (
                    <button
                      type="button"
                      onClick={startCamera}
                      disabled={uploadingPhoto}
                      style={{ ...secondaryButtonStyle, width: 'auto', marginTop: 0, padding: '6px 12px', fontSize: '13px' }}
                    >
                      📷 写真を撮る
                    </button>
                  )}
                </div>
                {cameraError && <p style={{ color: '#dc3545', fontSize: '13px' }}>{cameraError}</p>}
                {cameraStream !== null && (
                  <div style={{ marginTop: '8px' }}>
                    <video
                      ref={videoRef}
                      autoPlay
                      playsInline
                      muted
                      style={{ width: '100%', maxWidth: '320px', borderRadius: '4px', backgroundColor: '#000' }}
                    />
                    <div style={{ display: 'flex', gap: '8px', marginTop: '8px', maxWidth: '320px' }}>
                      <button type="button" onClick={capturePhoto} style={{ ...primaryButtonStyle, marginBottom: 0 }}>
                        撮影する
                      </button>
                      <button type="button" onClick={stopCamera} style={secondaryButtonStyle}>
                        キャンセル
                      </button>
                    </div>
                  </div>
                )}
                {uploadingPhoto && <p style={{ color: '#666', fontSize: '13px' }}>アップロード中...</p>}
                {itemForm.photoUrl && !uploadingPhoto && cameraStream === null && (
                  <div style={{ marginTop: '8px', display: 'flex', alignItems: 'flex-end', gap: '10px' }}>
                    <img
                      src={toPhotoDisplayUrl(itemForm.photoUrl)}
                      alt=""
                      style={{ maxWidth: '120px', maxHeight: '120px', borderRadius: '4px' }}
                      onError={(e) => {
                        (e.target as HTMLImageElement).style.display = 'none';
                      }}
                    />
                    <button type="button" onClick={clearPhoto} style={{ ...secondaryButtonStyle, width: 'auto', marginTop: 0, padding: '6px 12px', fontSize: '13px' }}>
                      写真を削除
                    </button>
                  </div>
                )}
              </FormField>
              <FormField label="並び順（小さいほど先に表示）">
                <input
                  type="number"
                  value={itemForm.displayOrder}
                  onChange={(e) => setItemForm((prev) => ({ ...prev, displayOrder: Number(e.target.value) || 0 }))}
                  style={getInputStyle('displayOrder')}
                />
              </FormField>
              <div style={{ marginBottom: '15px' }}>
                <label>
                  <input
                    type="checkbox"
                    checked={itemForm.active}
                    onChange={(e) => {
                      const active = e.target.checked;
                      setItemForm((prev) => ({ ...prev, active, salesStatus: active ? prev.salesStatus : 'SUSPENDED' }));
                    }}
                    style={{ marginRight: '8px' }}
                  />
                  有効にする
                </label>
              </div>
              <button type="submit" disabled={saving || categories.length === 0} style={primaryButtonStyle}>
                {saving ? '処理中...' : '保存する'}
              </button>
              <BackToListButton onClick={backToList} />
            </form>
          )}
        </>
      )}
    </div>
  );
};

const ItemList: React.FC<{
  items: MenuItem[];
  categoryById: Map<number, MenuCategory>;
  itemErrors: Record<number, string[]>;
  editable: boolean;
  toggleable: boolean;
  onEdit: (item: MenuItem) => void;
  onToggle: (item: MenuItem, nextStatus: SalesStatus) => void;
  onCreate: () => void;
  canCreate: boolean;
}> = ({ items, categoryById, itemErrors, editable, toggleable, onEdit, onToggle, onCreate, canCreate }) => (
  <div style={{ marginBottom: '15px' }}>
    {items.length === 0 && <p style={{ color: '#666' }}>該当するメニューがありません。</p>}
    {items.map((item) => {
      const categoryInactive = categoryById.get(item.categoryId)?.active === false;
      return (
      <div
        key={item.id}
        style={{
          padding: '12px 16px',
          marginBottom: '8px',
          border: '1px solid #ddd',
          borderRadius: '8px',
          background: item.active ? '#fff' : '#f5f5f5',
          opacity: item.active ? 1 : 0.6,
        }}
      >
        {itemErrors[item.id] && <TopMessage messages={itemErrors[item.id]} isError />}
        <div
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '8px' }}
          onClick={editable ? () => onEdit(item) : undefined}
          role={editable ? 'button' : undefined}
        >
          <div style={{ cursor: editable ? 'pointer' : 'default' }}>
            <div style={{ fontWeight: 600, display: 'flex', alignItems: 'center', gap: '6px' }}>
              <CategoryBadge name={item.categoryName} />
              <span>
                {item.name}
                {!item.active && '　[無効]'}
              </span>
            </div>
            <div style={{ fontSize: '13px', color: '#666', marginTop: '2px' }}>
              {item.priceJpy.toLocaleString()}円 ・ {TAX_CATEGORY_LABELS[item.taxCategory]}
            </div>
            {categoryInactive && (
              <div style={{ fontSize: '12px', color: '#dc3545', marginTop: '2px' }}>
                ⚠ カテゴリ「{item.categoryName}」は無効になっています
              </div>
            )}
          </div>
          <SalesStatusBadge status={item.salesStatus} />
        </div>
        {toggleable && (
          <div style={{ marginTop: '10px' }}>
            <SalesStatusButtons item={item} onToggle={onToggle} canEnable={item.active && !categoryInactive} />
          </div>
        )}
      </div>
      );
    })}
    {canCreate && (
      <button type="button" onClick={onCreate} style={addButtonStyle}>
        ＋ 新しいメニューを追加
      </button>
    )}
  </div>
);

const CategoryBadge: React.FC<{ name: string }> = ({ name }) => (
  <span
    style={{
      flexShrink: 0,
      padding: '1px 8px',
      borderRadius: '10px',
      fontSize: '11px',
      fontWeight: 400,
      color: '#555',
      backgroundColor: '#eee',
      border: '1px solid #ddd',
    }}
  >
    {name}
  </span>
);

const SalesStatusBadge: React.FC<{ status: SalesStatus }> = ({ status }) => (
  <span
    style={{
      flexShrink: 0,
      padding: '2px 8px',
      borderRadius: '10px',
      fontSize: '12px',
      color: '#fff',
      backgroundColor: SALES_STATUS_COLORS[status],
    }}
  >
    {SALES_STATUS_LABELS[status]}
  </span>
);

const SalesStatusButtons: React.FC<{
  item: MenuItem;
  onToggle: (item: MenuItem, nextStatus: SalesStatus) => void;
  /** メニュー項目・所属カテゴリが無効なら、提供停止以外への変更ボタンは出さない（バックエンドの不変条件と一致させる）。 */
  canEnable: boolean;
}> = ({ item, onToggle, canEnable }) => (
  <div style={{ display: 'flex', gap: '8px' }}>
    {(['ON_SALE', 'SOLD_OUT', 'SUSPENDED'] as const)
      .filter((s) => s !== item.salesStatus)
      .filter((s) => canEnable || s === 'SUSPENDED')
      .map((s) => (
        <button
          key={s}
          type="button"
          onClick={() => onToggle(item, s)}
          style={{
            flex: 1,
            padding: '6px',
            fontSize: '13px',
            backgroundColor: '#fff',
            color: '#333',
            border: '1px solid #ccc',
            borderRadius: '4px',
            cursor: 'pointer',
          }}
        >
          {SALES_STATUS_LABELS[s]}にする
        </button>
      ))}
  </div>
);

const CategoryList: React.FC<{
  categories: MenuCategory[];
  editable: boolean;
  onEdit: (category: MenuCategory) => void;
  onCreate: () => void;
}> = ({ categories, editable, onEdit, onCreate }) => (
  <div style={{ marginBottom: '15px' }}>
    {categories.length === 0 && <p style={{ color: '#666' }}>該当するカテゴリがありません。</p>}
    {categories.map((category) => (
      <button
        key={category.id}
        type="button"
        disabled={!editable}
        onClick={() => onEdit(category)}
        style={{
          display: 'block',
          width: '100%',
          textAlign: 'left',
          padding: '12px 16px',
          marginBottom: '8px',
          border: '1px solid #ddd',
          borderRadius: '8px',
          background: category.active ? '#fff' : '#f5f5f5',
          opacity: category.active ? 1 : 0.6,
          cursor: editable ? 'pointer' : 'default',
        }}
      >
        <div style={{ fontWeight: 600 }}>
          {category.name}
          {!category.active && '　[無効]'}
        </div>
        <div style={{ fontSize: '13px', color: '#666' }}>並び順 {category.displayOrder}</div>
      </button>
    ))}
    {editable && (
      <button type="button" onClick={onCreate} style={addButtonStyle}>
        ＋ 新しいカテゴリを追加
      </button>
    )}
  </div>
);

const TabButton: React.FC<{ label: string; active: boolean; onClick: () => void }> = ({
  label,
  active,
  onClick,
}) => (
  <button
    type="button"
    onClick={onClick}
    style={{
      padding: '8px 16px',
      border: 'none',
      borderBottom: active ? '2px solid #007bff' : '2px solid transparent',
      background: 'none',
      color: active ? '#007bff' : '#333',
      fontWeight: active ? 600 : 400,
      cursor: 'pointer',
    }}
  >
    {label}
  </button>
);

const FormField: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div style={{ marginBottom: '15px' }}>
    <label style={{ display: 'block', marginBottom: '5px' }}>{label}:</label>
    {children}
  </div>
);

const BackToListButton: React.FC<{ onClick: () => void }> = ({ onClick }) => (
  <button type="button" onClick={onClick} style={secondaryButtonStyle}>
    ← 一覧に戻る
  </button>
);

const primaryButtonStyle: React.CSSProperties = {
  width: '100%',
  padding: '10px',
  backgroundColor: '#007bff',
  color: '#fff',
  border: 'none',
  borderRadius: '4px',
  cursor: 'pointer',
};

const secondaryButtonStyle: React.CSSProperties = {
  width: '100%',
  padding: '10px',
  marginTop: '10px',
  backgroundColor: '#fff',
  color: '#333',
  border: '1px solid #ccc',
  borderRadius: '4px',
  cursor: 'pointer',
};

const addButtonStyle: React.CSSProperties = {
  width: '100%',
  padding: '10px',
  marginBottom: '15px',
  backgroundColor: '#fff',
  color: '#007bff',
  border: '1px dashed #007bff',
  borderRadius: '4px',
  cursor: 'pointer',
};
