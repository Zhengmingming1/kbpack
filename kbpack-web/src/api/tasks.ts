import { apiClient } from './client';
import type { PageResponse } from './packages';

export interface ParseTask {
  id: string;
  version_id: string;
  package_id?: string;
  package_title?: string;
  task_type: string;
  status: string;
  attempt_count: number;
  error_message?: string | null;
  created_at: string;
}

export async function listTasks(params: {
  status?: string;
  version_id?: string;
  page?: number;
  page_size?: number;
} = {}) {
  const { data } = await apiClient.get<PageResponse<ParseTask>>('/api/v1/tasks', { params });
  return data;
}

export async function retryTask(taskId: string) {
  const { data } = await apiClient.post<{ status: string }>(`/api/v1/tasks/${taskId}/retry`);
  return data;
}
