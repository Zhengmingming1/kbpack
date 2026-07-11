import { apiClient } from './client';

export interface TagItem {
  id: string;
  name: string;
  package_count?: number;
  created_at?: string;
}

export async function listTags() {
  const { data } = await apiClient.get<TagItem[] | { items: TagItem[] }>('/api/v1/tags');
  return Array.isArray(data) ? data : data.items;
}

export async function createTag(name: string) {
  const { data } = await apiClient.post<TagItem>('/api/v1/tags', { name });
  return data;
}

export async function deleteTag(tagId: string) {
  await apiClient.delete(`/api/v1/tags/${tagId}`);
}
