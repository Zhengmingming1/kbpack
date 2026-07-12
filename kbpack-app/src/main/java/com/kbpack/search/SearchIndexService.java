package com.kbpack.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbpack.common.config.KbpackProperties;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.parser.ExtractedDocument;
import com.kbpack.parser.ExtractedDocumentRepository;
import com.kbpack.parser.SearchChunk;
import com.kbpack.parser.SearchChunkRepository;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageVersion;
import com.kbpack.pkg.PackageVersionRepository;
import com.kbpack.pkg.PackageTagRepository;
import com.kbpack.pkg.PackageCollectionRepository;
import com.kbpack.pkg.TagRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SearchIndexService {
    private static final Pattern ACRONYM = Pattern.compile("\\b[A-Z]{2,6}\\b");
    private static final Pattern CAMEL = Pattern.compile("\\b[A-Z][a-z0-9]+(?:[A-Z][a-z0-9]+)+\\b");
    private final KbpackProperties properties;
    private final ObjectMapper objectMapper;
    private final SearchChunkRepository chunkRepository;
    private final ExtractedDocumentRepository documentRepository;
    private final KnowledgePackageRepository packageRepository;
    private final PackageTagRepository packageTagRepository;
    private final TagRepository tagRepository;
    private final PackageCollectionRepository packageCollectionRepository;
    private final PackageVersionRepository versionRepository;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public SearchIndexService(
            KbpackProperties properties,
            ObjectMapper objectMapper,
            SearchChunkRepository chunkRepository,
            ExtractedDocumentRepository documentRepository,
            KnowledgePackageRepository packageRepository,
            PackageTagRepository packageTagRepository,
            TagRepository tagRepository,
            PackageCollectionRepository packageCollectionRepository,
            PackageVersionRepository versionRepository
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.packageRepository = packageRepository;
        this.packageTagRepository = packageTagRepository;
        this.tagRepository = tagRepository;
        this.packageCollectionRepository = packageCollectionRepository;
        this.versionRepository = versionRepository;
    }

    public synchronized void indexVersion(
            KnowledgePackage pkg,
            PackageVersion version,
            List<ExtractedDocument> documents,
            List<SearchChunk> chunks
    ) {
        Map<UUID, ExtractedDocument> byId = new HashMap<>();
        documents.forEach(document -> byId.put(document.getId(), document));
        deleteByFilter("version_id = " + quoted(IdPrefix.VERSION.format(version.getId())));
        if (!chunks.isEmpty()) {
            mutate("/indexes/" + uid() + "/documents?primaryKey=id", documents(pkg, version, byId, chunks));
        }
    }

    public synchronized void deleteVersion(UUID versionId) {
        deleteByFilter("version_id = " + quoted(IdPrefix.VERSION.format(versionId)));
    }

    public synchronized void reindexAll() {
        List<Map<String, Object>> payload = new ArrayList<>();
        Map<UUID, ExtractedDocument> documents = new HashMap<>();
        documentRepository.findAll().forEach(document -> documents.put(document.getId(), document));
        Map<UUID, KnowledgePackage> packages = new HashMap<>();
        packageRepository.findAll().stream().filter(pkg -> pkg.getDeletedAt() == null)
                .forEach(pkg -> packages.put(pkg.getId(), pkg));
        for (SearchChunk chunk : chunkRepository.findAll()) {
            KnowledgePackage pkg = packages.get(chunk.getPackageId());
            ExtractedDocument document = documents.get(chunk.getDocumentId());
            if (pkg == null || document == null) continue;
            PackageVersion activeVersion = versionRepository.findActiveById(chunk.getVersionId()).orElse(null);
            if (activeVersion == null) continue;
            String entryFile = activeVersion.getEntryFile() == null
                    ? document.getSourcePath() : activeVersion.getEntryFile();
            payload.add(document(pkg, entryFile, document, chunk));
        }
        awaitTask(request("DELETE", "/indexes/" + uid() + "/documents", null));
        if (!payload.isEmpty()) mutate("/indexes/" + uid() + "/documents?primaryKey=id", payload);
    }

    public synchronized void reindexPackage(UUID packageId) {
        KnowledgePackage pkg = packageRepository.findActiveById(packageId).orElse(null);
        if (pkg == null) {
            deleteByFilter("package_id = " + quoted(IdPrefix.PACKAGE.format(packageId)));
            return;
        }

        Map<UUID, ExtractedDocument> documents = new HashMap<>();
        documentRepository.findByPackageId(packageId).forEach(document -> documents.put(document.getId(), document));
        Map<UUID, PackageVersion> versions = new HashMap<>();
        versionRepository.findActiveByPackageId(packageId).forEach(version -> versions.put(version.getId(), version));
        List<Map<String, Object>> payload = new ArrayList<>();
        for (SearchChunk chunk : chunkRepository.findByPackageId(packageId)) {
            ExtractedDocument document = documents.get(chunk.getDocumentId());
            PackageVersion version = versions.get(chunk.getVersionId());
            if (document == null || version == null) continue;
            String entryFile = version.getEntryFile() == null ? document.getSourcePath() : version.getEntryFile();
            payload.add(document(pkg, entryFile, document, chunk));
        }
        deleteByFilter("package_id = " + quoted(IdPrefix.PACKAGE.format(packageId)));
        if (!payload.isEmpty()) mutate("/indexes/" + uid() + "/documents?primaryKey=id", payload);
    }

    public SearchPage search(String query, int page, int pageSize, String filter) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("q", query);
        body.put("offset", (page - 1) * pageSize);
        body.put("limit", pageSize);
        body.put("attributesToHighlight", List.of("content", "heading", "document_title", "package_title"));
        body.put("highlightPreTag", "<em>");
        body.put("highlightPostTag", "</em>");
        if (filter != null && !filter.isBlank()) body.put("filter", filter);
        Map<String, Object> response = send("/indexes/" + uid() + "/search", body);
        long total = number(response.get("estimatedTotalHits"), number(response.get("totalHits"), 0));
        List<Map<String, Object>> items = new ArrayList<>();
        Object hits = response.get("hits");
        if (hits instanceof List<?> list) {
            for (Object value : list) {
                if (!(value instanceof Map<?, ?> raw)) continue;
                Map<String, Object> hit = stringMap(raw);
                Map<String, Object> formatted = hit.get("_formatted") instanceof Map<?, ?> map ? stringMap(map) : Map.of();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("package_id", hit.get("package_id"));
                item.put("package_title", hit.get("package_title"));
                item.put("version_id", hit.get("version_id"));
                item.put("document_id", hit.get("document_id"));
                item.put("document_title", hit.get("document_title"));
                item.put("snippet", formatted.getOrDefault("content", hit.get("content")));
                item.put("heading", formatted.getOrDefault("heading", hit.get("heading")));
                item.put("tags", hit.getOrDefault("tags", List.of()));
                item.put("updated_at", hit.get("updated_at"));
                item.put("preview_url", hit.get("preview_url"));
                item.put("anchor", hit.get("anchor"));
                items.add(item);
            }
        }
        return new SearchPage(total, items);
    }

    public record SearchPage(long total, List<Map<String, Object>> items) {}

    private List<Map<String, Object>> documents(
            KnowledgePackage pkg,
            PackageVersion version,
            Map<UUID, ExtractedDocument> documents,
            List<SearchChunk> chunks
    ) {
        return chunks.stream().map(chunk -> document(pkg, version.getEntryFile(),
                documents.get(chunk.getDocumentId()), chunk)).toList();
    }

    private Map<String, Object> document(
            KnowledgePackage pkg,
            String entryFile,
            ExtractedDocument document,
            SearchChunk chunk
    ) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", IdPrefix.CHUNK.format(chunk.getId()));
        value.put("package_id", IdPrefix.PACKAGE.format(chunk.getPackageId()));
        value.put("version_id", IdPrefix.VERSION.format(chunk.getVersionId()));
        value.put("document_id", IdPrefix.DOCUMENT.format(chunk.getDocumentId()));
        value.put("package_title", pkg.getTitle());
        value.put("document_title", document == null ? "" : document.getTitle());
        value.put("heading", chunk.getHeading());
        value.put("content", chunk.getContent());
        value.put("content_enhanced", enhance(chunk.getContent(), document == null ? "" : document.getSourcePath()));
        List<UUID> tagIds = packageTagRepository.findAllByIdPackageId(pkg.getId()).stream()
                .map(link -> link.getId().getTagId()).toList();
        value.put("tags", tagRepository.findAllById(tagIds).stream().map(tag -> tag.getName()).sorted().toList());
        value.put("collection_ids", packageCollectionRepository.findAllByIdPackageId(pkg.getId()).stream()
                .map(link -> IdPrefix.COLLECTION.format(link.getId().getCollectionId())).toList());
        value.put("status", pkg.getStatus().name());
        value.put("source_type", pkg.getSourceType().name());
        value.put("owner_id", IdPrefix.USER.format(pkg.getOwnerId()));
        value.put("visibility", pkg.getVisibility());
        value.put("source_path", document == null ? "" : document.getSourcePath());
        value.put("anchor", chunk.getAnchor());
        value.put("preview_url", "/p/" + IdPrefix.PACKAGE.format(pkg.getId()) + "/v/"
                + IdPrefix.VERSION.format(chunk.getVersionId()) + "/" + (entryFile == null ? "" : entryFile)
                + (chunk.getAnchor() == null ? "" : "#" + chunk.getAnchor()));
        value.put("created_at", pkg.getCreatedAt().getEpochSecond());
        value.put("updated_at", pkg.getUpdatedAt().toString());
        return value;
    }

    static String enhance(String content, String sourcePath) {
        String value = content == null ? "" : content;
        StringBuilder extra = new StringBuilder();
        Matcher acronym = ACRONYM.matcher(value);
        while (acronym.find()) extra.append(' ').append(acronym.group());
        Matcher camel = CAMEL.matcher(value);
        while (camel.find()) {
            extra.append(' ').append(camel.group().replaceAll("(?<=[a-z0-9])(?=[A-Z])", " "));
        }
        if (sourcePath != null) {
            extra.append(' ').append(sourcePath.replaceAll("\\.[^.]+$", "").replaceAll("[/_-]+", " "));
        }
        return value + extra;
    }

    private void deleteByFilter(String filter) {
        mutate("/indexes/" + uid() + "/documents/delete", Map.of("filter", filter));
    }

    private Map<String, Object> send(String path, Object body) {
        return request("POST", path, body);
    }

    private void mutate(String path, Object body) {
        awaitTask(request("POST", path, body));
    }

    private void awaitTask(Map<String, Object> submitted) {
        long taskUid = number(submitted.get("taskUid"), -1);
        if (taskUid < 0) return;
        long timeoutSeconds = Math.min(3_600, Math.max(30,
                properties.getSearch().getMeilisearch().getTaskTimeoutSeconds()));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            Map<String, Object> task = request("GET", "/tasks/" + taskUid, null);
            String status = String.valueOf(task.getOrDefault("status", ""));
            if ("succeeded".equals(status)) return;
            if ("failed".equals(status) || "canceled".equals(status)) {
                Object error = task.get("error");
                throw new ApiException(ErrorCode.SEARCH_UNAVAILABLE,
                        "Search index task " + taskUid + " " + status + (error == null ? "" : ": " + error));
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new ApiException(ErrorCode.SEARCH_UNAVAILABLE, "Interrupted while waiting for search index task");
            }
        }
        throw new ApiException(ErrorCode.SEARCH_UNAVAILABLE, "Timed out waiting for search index task " + taskUid);
    }

    private Map<String, Object> request(String method, String path, Object body) {
        try {
            String host = properties.getSearch().getMeilisearch().getHost().replaceAll("/+$", "");
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(host + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + properties.getSearch().getMeilisearch().getApiKey())
                    .header("Content-Type", "application/json");
            if ("GET".equals(method)) {
                builder.GET();
            } else if ("DELETE".equals(method) && body == null) {
                builder.DELETE();
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            }
            HttpRequest request = builder.build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(ErrorCode.SEARCH_UNAVAILABLE, "搜索服务返回 " + response.statusCode());
            }
            if (response.body() == null || response.body().isBlank()) return Map.of();
            return objectMapper.readValue(response.body(), new TypeReference<>() {});
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.SEARCH_UNAVAILABLE, "搜索服务暂不可用");
        }
    }

    private String uid() {
        return URLEncoder.encode(properties.getSearch().getMeilisearch().getIndexUid(), StandardCharsets.UTF_8);
    }

    private static String quoted(String value) { return "\"" + value.replace("\"", "\\\"") + "\""; }
    private static long number(Object value, long fallback) { return value instanceof Number n ? n.longValue() : fallback; }
    private static Map<String, Object> stringMap(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
}
