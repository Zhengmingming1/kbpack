import { apiClient } from './client';

export interface UserInfo {
  id: string;
  username: string;
  display_name: string;
  role: string;
}

export async function login(username: string, password: string) {
  const { data } = await apiClient.post<{ user: UserInfo }>('/api/v1/auth/login', {
    username,
    password,
  });
  return data.user;
}

export async function logout() {
  await apiClient.post('/api/v1/auth/logout');
}

export async function me() {
  const { data } = await apiClient.get<UserInfo | { user: UserInfo }>('/api/v1/auth/me');
  return 'user' in data ? data.user : data;
}
