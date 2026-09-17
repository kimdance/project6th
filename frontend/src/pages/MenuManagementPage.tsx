import React, { useEffect, useState } from 'react';
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
  const [saving, setSaving] = useState(false);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);

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

  const selectStoreData = async (storeId: number) => {
    setSelectedStoreId(storeId);
    const [categoryList, itemList] = await Promise.all([fetchMenuCategories(storeId), fetchMenuItems(storeId)]);
    setCategories(categoryList);
    setItems(itemList);
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
        setCategories(await fetchMenuCategories(selectedStoreId));
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
    resetMessages();
    setEditingItemId(null);
    setEditingItem(null);
    setItemForm(emptyItemForm(categories[0]?.id ?? 0));
    setTab('items');
    setView('form');
  };

  const openEditItem = (item: MenuItem) => {
    resetMessages();
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
    });
    setTab('items');
    setView('form');
  };

  const handleItemSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedStoreId === null) {
      return;
    }
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

  const handlePhotoFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file || selectedStoreId === null) {
      return;
    }
    resetMessages();
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

  const clearPhoto = () => {
    setItemForm((prev) => ({ ...prev, photoUrl: '' }));
  };

  const toggleStatus = async (item: MenuItem, nextStatus: SalesStatus) => {
    if (selectedStoreId === null) {
      return;
    }
    resetMessages();
    const result = await updateMenuItemSalesStatus(selectedStoreId, item.id, nextStatus);
    if (result.ok) {
      setItems((prev) => prev.map((i) => (i.id === item.id ? result.item : i)));
      setEditingItem((prev) => (prev && prev.id === item.id ? result.item : prev));
      setSuccessMessage(`「${result.item.name}」を${SALES_STATUS_LABELS[nextStatus]}にしました。`);
    } else {
      setMessages(result.errors.map((err) => err.message));
    }
  };

  const backToList = () => {
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
                <div
                  style={{
                    marginBottom: '15px',
                    padding: '10px 12px',
                    border: '1px solid #ddd',
                    borderRadius: '8px',
                    background: '#fafafa',
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginBottom: '8px',
                    }}
                  >
                    <span style={{ fontSize: '13px', color: '#666' }}>販売状況</span>
                    <SalesStatusBadge status={editingItem.salesStatus} />
                  </div>
                  <SalesStatusButtons item={editingItem} onToggle={toggleStatus} />
                </div>
              )}
              <FormField label="カテゴリ">
                <select
                  value={itemForm.categoryId}
                  onChange={(e) => {
                    setItemForm((prev) => ({ ...prev, categoryId: Number(e.target.value) }));
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
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  capture="environment"
                  onChange={handlePhotoFileChange}
                  disabled={uploadingPhoto}
                />
                {uploadingPhoto && <p style={{ color: '#666', fontSize: '13px' }}>アップロード中...</p>}
                {itemForm.photoUrl && !uploadingPhoto && (
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
                    onChange={(e) => setItemForm((prev) => ({ ...prev, active: e.target.checked }))}
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
  editable: boolean;
  toggleable: boolean;
  onEdit: (item: MenuItem) => void;
  onToggle: (item: MenuItem, nextStatus: SalesStatus) => void;
  onCreate: () => void;
  canCreate: boolean;
}> = ({ items, categoryById, editable, toggleable, onEdit, onToggle, onCreate, canCreate }) => (
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
            <SalesStatusButtons item={item} onToggle={onToggle} />
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

const SalesStatusButtons: React.FC<{ item: MenuItem; onToggle: (item: MenuItem, nextStatus: SalesStatus) => void }> = ({
  item,
  onToggle,
}) => (
  <div style={{ display: 'flex', gap: '8px' }}>
    {(['ON_SALE', 'SOLD_OUT', 'SUSPENDED'] as const)
      .filter((s) => s !== item.salesStatus)
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
