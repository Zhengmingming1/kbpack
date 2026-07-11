import axios from 'axios';

export interface ApiErrorBody {
  code?: number;
  message?: string;
  trace_id?: string;
}

/**
 * Shared axios instance for session-authenticated main-site requests.
 * Vite dev server proxies /api and /health to localhost:18080.
 */
export const apiClient = axios.create({
  baseURL: '',
  timeout: 30000,
  withCredentials: true,
  headers: {
    Accept: 'application/json',
    'X-Requested-With': 'XMLHttpRequest',
  },
});

apiClient.interceptors.response.use(
  (res) => res,
  (error) => {
    if (
      axios.isAxiosError(error) &&
      error.response?.status === 401 &&
      window.location.pathname !== '/login'
    ) {
      const from = `${window.location.pathname}${window.location.search}${window.location.hash}`;
      window.location.replace(`/login?from=${encodeURIComponent(from)}`);
    }
    return Promise.reject(error);
  },
);

export function getApiErrorMessage(error: unknown, fallback = '请求失败，请稍后重试') {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    return error.response?.data?.message || error.message || fallback;
  }
  return error instanceof Error ? error.message : fallback;
}
