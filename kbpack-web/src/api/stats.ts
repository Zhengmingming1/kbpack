import { apiClient } from './client';

export interface Stats {
  package_count: number;
  document_count?: number;
  storage_used_bytes?: number;
  parse_failed_count?: number;
  tag_count?: number;
  user_count?: number;
  recent_uploads?: Array<{ package_id: string; title: string; created_at: string }>;
}

export async function getStats() {
  const { data } = await apiClient.get<Stats>('/api/v1/stats');
  return data;
}
