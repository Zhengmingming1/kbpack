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

export type VersionDiffChangeType = 'added' | 'removed' | 'modified' | 'unchanged';

export interface VersionDiffMetadataChange {
  field: string;
  change_type: VersionDiffChangeType;
  before: unknown;
  after: unknown;
}

export interface VersionDiffDocumentMetadata {
  title?: string | null;
  doc_type?: DocumentType | null;
  word_count?: number | null;
  order_no?: number | null;
  content_length?: number;
  content_hash?: string | null;
}

export interface VersionDiffLine {
  type: 'context' | 'added' | 'removed';
  old_line?: number | null;
  new_line?: number | null;
  text: string;
}

export interface VersionDiffHunk {
  old_start: number;
  old_count: number;
  new_start: number;
  new_count: number;
  lines: VersionDiffLine[];
}

export interface VersionDiffDocumentChange {
  source_path: string;
  change_type: VersionDiffChangeType;
  fields_changed: string[];
  before?: VersionDiffDocumentMetadata | null;
  after?: VersionDiffDocumentMetadata | null;
  content_diff?: {
    status: 'available' | 'unchanged' | 'omitted_limit' | 'unavailable';
    changed: boolean;
    hunks: VersionDiffHunk[];
    truncated: boolean;
  } | null;
}

export interface VersionDiffVersion {
  version_id: string;
  version_no: number;
  original_filename: string;
  content_hash?: string | null;
  entry_file?: string | null;
  unpacked_size?: number | null;
  file_count?: number | null;
  parse_status: string;
  created_at: string;
  is_current: boolean;
}

export interface VersionDiffAssetMetadata {
  mime_type?: string | null;
  size: number;
  sha256: string;
  role?: string | null;
}

export interface VersionDiffAssetChange {
  path: string;
  change_type: VersionDiffChangeType;
  fields_changed: string[];
  before?: VersionDiffAssetMetadata | null;
  after?: VersionDiffAssetMetadata | null;
}

export interface VersionDiffResult {
  package_id: string;
  base_version: VersionDiffVersion;
  target_version: VersionDiffVersion;
  metadata_changes: VersionDiffMetadataChange[];
  document_summary: {
    total_paths: number;
    compared_paths: number;
    added: number;
    removed: number;
    modified: number;
    unchanged: number;
    returned: number;
  };
  document_changes: VersionDiffDocumentChange[];
  asset_summary: {
    total_paths: number;
    compared_paths: number;
    added: number;
    removed: number;
    modified: number;
    unchanged: number;
    returned: number;
  };
  asset_changes: VersionDiffAssetChange[];
  truncated: boolean;
  limits?: {
    max_documents: number;
    max_assets: number;
    max_detailed_documents: number;
    max_source_characters_per_document: number;
    max_source_lines_per_document: number;
    max_diff_lines_per_document: number;
    max_diff_lines_total: number;
    max_line_characters: number;
    context_lines: number;
  };
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

export async function getVersionDiff(
  packageId: string,
  baseVersionId: string,
  targetVersionId: string,
) {
  const { data } = await apiClient.get<VersionDiffResult>(
    `/api/v1/packages/${packageId}/versions/diff`,
    {
      params: {
        base_version_id: baseVersionId,
        target_version_id: targetVersionId,
      },
    },
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
