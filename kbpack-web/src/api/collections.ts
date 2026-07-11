import { apiClient } from './client';

export interface CollectionItem {
  id: string;
  name: string;
  parent_id?: string | null;
  sort_order?: number;
  package_count?: number;
  created_at?: string;
  children?: CollectionItem[];
}

export async function listCollections() {
  const { data } = await apiClient.get<CollectionItem[] | { items: CollectionItem[] }>(
    '/api/v1/collections',
  );
  return Array.isArray(data) ? data : data.items;
}

export async function createCollection(values: {
  name: string;
  parent_id?: string | null;
  sort_order?: number;
}) {
  const { data } = await apiClient.post<CollectionItem>('/api/v1/collections', values);
  return data;
}

export async function updateCollection(
  collectionId: string,
  values: { name?: string; parent_id?: string | null; sort_order?: number },
) {
  const { data } = await apiClient.patch<CollectionItem>(
    `/api/v1/collections/${collectionId}`,
    values,
  );
  return data;
}

export async function deleteCollection(collectionId: string) {
  await apiClient.delete(`/api/v1/collections/${collectionId}`);
}
