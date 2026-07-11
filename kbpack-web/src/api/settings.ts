import { apiClient } from './client';

export type SystemSettings = Record<string, string | number | boolean | null>;

export async function getSettings() {
  const { data } = await apiClient.get<SystemSettings>('/api/v1/settings');
  return data;
}

export async function updateSettings(values: SystemSettings) {
  const { data } = await apiClient.patch<SystemSettings>('/api/v1/settings', values);
  return data;
}

export interface HealthStatus {
  status: string;
  detail?: string;
  service?: string;
  timestamp?: string;
}

export async function getHealth(kind?: 'db' | 'search' | 'storage') {
  const { data } = await apiClient.get<HealthStatus>(kind ? `/health/${kind}` : '/health');
  return data;
}
