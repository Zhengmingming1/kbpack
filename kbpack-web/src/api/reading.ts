import { apiClient } from './client';

export interface ReadingListItem {
  document_id: string;
  document_title?: string;
  title?: string;
  package_id?: string;
  package_title?: string;
  version_id?: string;
  progress?: number;
  is_bookmarked?: boolean;
  bookmarked?: boolean;
  last_read_at?: string;
  updated_at?: string;
}

export interface DocumentReadingState {
  document_id: string;
  progress: number;
  is_bookmarked?: boolean;
  bookmarked?: boolean;
  updated_at?: string;
}

function itemsFrom(data: ReadingListItem[] | { items: ReadingListItem[] }) {
  return Array.isArray(data) ? data : data.items;
}

export async function listRecentReading() {
  const { data } = await apiClient.get<ReadingListItem[] | { items: ReadingListItem[] }>(
    '/api/v1/reading/recent',
  );
  return itemsFrom(data);
}

export async function listReadingBookmarks() {
  const { data } = await apiClient.get<ReadingListItem[] | { items: ReadingListItem[] }>(
    '/api/v1/reading/bookmarks',
  );
  return itemsFrom(data);
}

export async function getDocumentReadingState(documentId: string) {
  const { data } = await apiClient.get<DocumentReadingState>(
    `/api/v1/reading/documents/${encodeURIComponent(documentId)}/state`,
  );
  return data;
}

export async function updateDocumentProgress(documentId: string, progress: number) {
  const { data } = await apiClient.put<DocumentReadingState>(
    `/api/v1/reading/documents/${encodeURIComponent(documentId)}/progress`,
    { progress },
  );
  return data;
}

export async function updateDocumentBookmark(documentId: string, bookmarked: boolean) {
  const { data } = await apiClient.put<DocumentReadingState>(
    `/api/v1/reading/documents/${encodeURIComponent(documentId)}/bookmark`,
    { bookmarked },
  );
  return data;
}

export function readingItemTitle(item: ReadingListItem) {
  return item.document_title || item.title || '未命名章节';
}

export function readingItemBookmarked(item: ReadingListItem | DocumentReadingState | undefined) {
  return Boolean(item?.is_bookmarked ?? item?.bookmarked);
}
