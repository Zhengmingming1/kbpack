import type { PageResponse } from './packages';
import { apiClient } from './client';

export interface SearchResult {
  package_id: string;
  package_title: string;
  document_id: string;
  document_title: string;
  snippet: string;
  tags: string[];
  updated_at: string;
  preview_url?: string;
  anchor?: string;
}

export interface SearchFilters {
  q: string;
  tag?: string;
  collection?: string;
  source?: string;
  status?: string;
  page?: number;
  page_size?: number;
  package_id?: string;
}

export async function searchKnowledge(filters: SearchFilters) {
  const { data } = await apiClient.get<PageResponse<SearchResult>>('/api/v1/search', {
    params: filters,
  });
  return data;
}

export async function rebuildSearchIndex() {
  const { data } = await apiClient.post<{ status: string }>('/api/v1/search/reindex');
  return data;
}
