import { apiClient } from './client';

export interface PackageVersion {
  id: string;
  version_no: number;
  original_filename: string;
  content_hash?: string;
  entry_file?: string | null;
  parse_status: string;
  parse_error?: string | null;
  unpacked_size?: number;
  file_count?: number;
  is_current?: boolean;
  created_at: string;
}

export interface FileNode {
  path: string;
  type: 'file' | 'dir';
  size?: number;
  role?: string;
  children?: FileNode[];
}

export type DocumentType = 'markdown' | 'html' | 'content_js' | 'text';

export interface DocumentSummary {
  id: string;
  title: string;
  doc_type: DocumentType;
  order_no: number;
  word_count?: number;
}

export interface DocumentDetail extends DocumentSummary {
  content: string;
  source_path?: string;
  heading_tree?: Array<{ level: number; text: string; anchor: string }>;
  prev_document_id?: string | null;
  next_document_id?: string | null;
  package_id?: string;
  version_id?: string;
}

export interface PreviewTicket {
  ticket: string;
  expires_in: number;
  preview_url: string;
}

export async function listVersions(packageId: string) {
  const { data } = await apiClient.get<PackageVersion[]>(`/api/v1/packages/${packageId}/versions`);
  return data;
}

export async function getVersion(packageId: string, versionId: string) {
  const { data } = await apiClient.get<PackageVersion>(
    `/api/v1/packages/${packageId}/versions/${versionId}`,
  );
  return data;
}

export async function setCurrentVersion(packageId: string, versionId: string) {
  const { data } = await apiClient.post<{ current_version_id: string }>(
    `/api/v1/packages/${packageId}/versions/${versionId}/set-current`,
  );
  return data;
}

export async function deleteVersion(packageId: string, versionId: string) {
  await apiClient.delete(`/api/v1/packages/${packageId}/versions/${versionId}`);
}

export async function reparseVersion(versionId: string, entryFile?: string) {
  const { data } = await apiClient.post<{ parse_status: string }>(
    `/api/v1/versions/${versionId}/reparse`,
    entryFile ? { entry_file: entryFile } : {},
  );
  return data;
}

export async function getFiles(versionId: string) {
  const { data } = await apiClient.get<{ tree: FileNode[] }>(`/api/v1/versions/${versionId}/files`);
  return data.tree;
}

export async function listDocuments(versionId: string) {
  const { data } = await apiClient.get<DocumentSummary[]>(`/api/v1/versions/${versionId}/documents`);
  return data;
}

export async function getDocument(documentId: string) {
  const { data } = await apiClient.get<DocumentDetail>(`/api/v1/documents/${documentId}`);
  return data;
}

export async function createPreviewTicket(packageId: string, versionId: string) {
  const { data } = await apiClient.post<PreviewTicket>(
    `/api/v1/packages/${packageId}/versions/${versionId}/preview-ticket`,
  );
  return data;
}
