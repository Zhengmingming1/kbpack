import { apiClient } from './client';

export interface PageResponse<T> {
  total: number;
  page: number;
  page_size: number;
  items: T[];
}

export interface CollectionRef {
  id: string;
  name: string;
}

export interface PackageListItem {
  id: string;
  title: string;
  description?: string;
  cover_url?: string;
  status: string;
  visibility: string;
  source_type?: string;
  source_name?: string;
  tags: string[];
  collections?: CollectionRef[];
  is_favorite?: boolean;
  created_at: string;
  updated_at: string;
  current_version?: {
    id: string;
    version_no: number;
    parse_status: string;
  };
  file_count?: number;
  unpacked_size?: number;
}

export interface PackageDetail extends PackageListItem {
  chapters?: Array<{ document_id: string; title: string; order_no: number }>;
  versions_count?: number;
  quality_notes?: Array<{ type?: string; text?: string; source?: string }> | Record<string, unknown>;
}

export interface PackageFilters {
  keyword?: string;
  tag?: string;
  collection?: string;
  status?: string;
  source?: string;
  favorite?: boolean;
  page?: number;
  page_size?: number;
}

export async function listPackages(filters: PackageFilters = {}) {
  const { data } = await apiClient.get<PageResponse<PackageListItem>>('/api/v1/packages', {
    params: filters,
  });
  return data;
}

export async function getPackage(packageId: string) {
  const { data } = await apiClient.get<PackageDetail>(`/api/v1/packages/${packageId}`);
  return data;
}

export async function updatePackage(
  packageId: string,
  values: Partial<Pick<PackageListItem, 'title' | 'description' | 'status' | 'visibility'>>,
) {
  const { data } = await apiClient.patch<PackageDetail>(`/api/v1/packages/${packageId}`, values);
  return data;
}

export async function deletePackage(packageId: string) {
  await apiClient.delete(`/api/v1/packages/${packageId}`);
}

export async function archivePackage(packageId: string) {
  const { data } = await apiClient.post<{ status: string }>(`/api/v1/packages/${packageId}/archive`);
  return data;
}

export async function setFavorite(packageId: string, favorite: boolean) {
  const path = `/api/v1/packages/${packageId}/favorite`;
  if (favorite) await apiClient.post(path);
  else await apiClient.delete(path);
}

export interface UploadMetadata {
  title?: string;
  description?: string;
  collection_ids?: string[];
  tag_names?: string[];
  source_type?: string;
  source_name?: string;
  entry_file?: string;
  target_package_id?: string;
}

export interface UploadResult {
  package_id: string;
  version_id: string;
  parse_status: string;
}

export async function uploadPackage(
  file: File,
  metadata: UploadMetadata,
  onProgress?: (percent: number) => void,
) {
  const form = new FormData();
  form.append('file', file);
  Object.entries(metadata).forEach(([key, value]) => {
    if (value === undefined || value === '' || (Array.isArray(value) && value.length === 0)) return;
    if (Array.isArray(value)) value.forEach((item) => form.append(key, item));
    else form.append(key, value);
  });
  const { data } = await apiClient.post<UploadResult>('/api/v1/packages/upload', form, {
    timeout: 10 * 60 * 1000,
    onUploadProgress: (event) => {
      if (event.total) onProgress?.(Math.round((event.loaded / event.total) * 100));
    },
  });
  return data;
}

export function packageDownloadUrl(versionId: string) {
  return `/api/v1/versions/${encodeURIComponent(versionId)}/files/download`;
}
