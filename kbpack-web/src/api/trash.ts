import { apiClient } from './client';
import type { PackageListItem, PageResponse } from './packages';

export interface TrashPackageItem extends PackageListItem {
  deleted_at?: string;
  purge_at?: string;
  can_restore?: boolean;
}

export async function listTrashPackages(params: { page?: number; page_size?: number } = {}) {
  const { data } = await apiClient.get<PageResponse<TrashPackageItem> | TrashPackageItem[]>(
    '/api/v1/trash/packages',
    { params },
  );
  if (!Array.isArray(data)) return data;
  return {
    total: data.length,
    page: 1,
    page_size: data.length || params.page_size || 20,
    items: data,
  };
}

export async function restoreTrashPackage(packageId: string) {
  const { data } = await apiClient.post<PackageListItem>(
    `/api/v1/trash/packages/${encodeURIComponent(packageId)}/restore`,
  );
  return data;
}

export async function permanentlyDeleteTrashPackage(packageId: string) {
  await apiClient.delete(`/api/v1/trash/packages/${encodeURIComponent(packageId)}`);
}
