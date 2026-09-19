import { authedFetch, authedUpload, extractErrors, type ErrorItem } from './http';
import { getTenantApiBaseUrl } from '../config';

export type TaxCategory = 'STANDARD_10' | 'REDUCED_8';
export type PrepType = 'COOK' | 'NO_COOK';
export type SalesStatus = 'ON_SALE' | 'SOLD_OUT' | 'SUSPENDED';

/** メニューカテゴリ（例：刺身、揚げ物、ドリンク）。FR-D02。 */
export interface MenuCategory {
  id: number;
  name: string;
  displayOrder: number;
  active: boolean;
}

export interface MenuCategoryRequest {
  name: string;
  displayOrder: number;
  active: boolean;
}

/** メニュー項目。FR-D01・D03。 */
export interface MenuItem {
  id: number;
  categoryId: number;
  categoryName: string;
  name: string;
  description: string | null;
  priceJpy: number;
  taxCategory: TaxCategory;
  prepType: PrepType;
  photoUrl: string | null;
  /** "HH:mm:ss" 形式（未設定なら null）。 */
  serveTimeFrom: string | null;
  serveTimeTo: string | null;
  salesStatus: SalesStatus;
  displayOrder: number;
  active: boolean;
}

export interface MenuItemRequest {
  categoryId: number;
  name: string;
  description: string;
  priceJpy: number;
  taxCategory: TaxCategory;
  prepType: PrepType;
  photoUrl: string;
  serveTimeFrom: string | null;
  serveTimeTo: string | null;
  displayOrder: number;
  active: boolean;
  /** 更新時のみ使う（新規登録時はサーバー側で決める）。 */
  salesStatus: SalesStatus;
}

export async function fetchMenuCategories(storeId: number): Promise<MenuCategory[]> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/menu-categories`);
  if (!res.ok) {
    return [];
  }
  return (await res.json()) as MenuCategory[];
}

export async function createMenuCategory(
  storeId: number,
  body: MenuCategoryRequest
): Promise<{ ok: true; category: MenuCategory } | { ok: false; errors: ErrorItem[] }> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/menu-categories`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, category: (await res.json()) as MenuCategory };
  }
  return { ok: false, errors: await extractErrors(res) };
}

export async function updateMenuCategory(
  storeId: number,
  categoryId: number,
  body: MenuCategoryRequest
): Promise<{ ok: true; category: MenuCategory } | { ok: false; errors: ErrorItem[] }> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/menu-categories/${categoryId}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, category: (await res.json()) as MenuCategory };
  }
  return { ok: false, errors: await extractErrors(res) };
}

/**
 * メニュー写真をアップロードし、保存先の相対パス（{@code /uploads/...}）を返す（FR-D01）。
 * このパスをそのまま {@link MenuItemRequest.photoUrl} に渡す。表示（img の src）には
 * {@link toPhotoDisplayUrl} でテナントのバックエンドのアドレスを補って使う。
 */
export async function uploadMenuItemPhoto(
  storeId: number,
  file: File
): Promise<{ ok: true; photoUrl: string } | { ok: false; errors: ErrorItem[] }> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await authedUpload(`/api/v1/stores/${storeId}/menu-items/photo`, formData);
  if (res.ok) {
    const body = (await res.json()) as { photoUrl: string };
    return { ok: true, photoUrl: body.photoUrl };
  }
  return { ok: false, errors: await extractErrors(res) };
}

/** 保存された photoUrl（相対パス）を、そのまま img の src に使える絶対URLへ変換する。 */
export function toPhotoDisplayUrl(photoUrl: string): string {
  return photoUrl.startsWith('/') ? `${getTenantApiBaseUrl()}${photoUrl}` : photoUrl;
}

export async function fetchMenuItems(storeId: number): Promise<MenuItem[]> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/menu-items`);
  if (!res.ok) {
    return [];
  }
  return (await res.json()) as MenuItem[];
}

export async function createMenuItem(
  storeId: number,
  body: MenuItemRequest
): Promise<{ ok: true; item: MenuItem } | { ok: false; errors: ErrorItem[] }> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/menu-items`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, item: (await res.json()) as MenuItem };
  }
  return { ok: false, errors: await extractErrors(res) };
}

export async function updateMenuItem(
  storeId: number,
  itemId: number,
  body: MenuItemRequest
): Promise<{ ok: true; item: MenuItem } | { ok: false; errors: ErrorItem[] }> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/menu-items/${itemId}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
  if (res.ok) {
    return { ok: true, item: (await res.json()) as MenuItem };
  }
  return { ok: false, errors: await extractErrors(res) };
}

export async function updateMenuItemSalesStatus(
  storeId: number,
  itemId: number,
  salesStatus: SalesStatus
): Promise<{ ok: true; item: MenuItem } | { ok: false; errors: ErrorItem[] }> {
  const res = await authedFetch(`/api/v1/stores/${storeId}/menu-items/${itemId}/sales-status`, {
    method: 'PATCH',
    body: JSON.stringify({ salesStatus }),
  });
  if (res.ok) {
    return { ok: true, item: (await res.json()) as MenuItem };
  }
  return { ok: false, errors: await extractErrors(res) };
}
