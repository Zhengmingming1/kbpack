package com.kbpack.pkg;

import com.kbpack.common.id.IdPrefix;
import com.kbpack.user.AppUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PackageViewService {

    private final PackageVersionRepository versionRepository;
    private final PackageTagRepository packageTagRepository;
    private final TagRepository tagRepository;
    private final PackageCollectionRepository packageCollectionRepository;
    private final CollectionRepository collectionRepository;
    private final FavoriteRepository favoriteRepository;
    private final JdbcTemplate jdbcTemplate;

    public PackageViewService(
            PackageVersionRepository versionRepository,
            PackageTagRepository packageTagRepository,
            TagRepository tagRepository,
            PackageCollectionRepository packageCollectionRepository,
            CollectionRepository collectionRepository,
            FavoriteRepository favoriteRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.versionRepository = versionRepository;
        this.packageTagRepository = packageTagRepository;
        this.tagRepository = tagRepository;
        this.packageCollectionRepository = packageCollectionRepository;
        this.collectionRepository = collectionRepository;
        this.favoriteRepository = favoriteRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listItem(KnowledgePackage pkg, AppUser user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", IdPrefix.PACKAGE.format(pkg.getId()));
        body.put("title", pkg.getTitle());
        body.put("description", pkg.getDescription());
        body.put("cover_url", pkg.getCoverAssetPath());
        body.put("status", pkg.getStatus().name());
        body.put("visibility", pkg.getVisibility());
        body.put("source_type", pkg.getSourceType().name());
        body.put("source_name", pkg.getSourceName());
        body.put("tags", tagNames(pkg.getId()));
        body.put("collections", collectionViews(pkg.getId()));
        body.put("is_favorite", favoriteRepository.existsByIdUserIdAndIdPackageId(user.getId(), pkg.getId()));
        body.put("created_at", pkg.getCreatedAt().toString());
        body.put("updated_at", pkg.getUpdatedAt().toString());

        PackageVersion currentVersion = pkg.getCurrentVersionId() == null
                ? null
                : versionRepository.findActiveById(pkg.getCurrentVersionId()).orElse(null);
        if (currentVersion == null) {
            body.put("current_version", null);
            body.put("file_count", 0);
            body.put("unpacked_size", 0L);
        } else {
            Map<String, Object> current = new LinkedHashMap<>();
            current.put("id", IdPrefix.VERSION.format(currentVersion.getId()));
            current.put("version_no", currentVersion.getVersionNo());
            current.put("parse_status", currentVersion.getParseStatus().name());
            body.put("current_version", current);
            body.put("file_count", currentVersion.getFileCount() == null ? 0 : currentVersion.getFileCount());
            body.put("unpacked_size", currentVersion.getUnpackedSize() == null ? 0L : currentVersion.getUnpackedSize());
        }
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(KnowledgePackage pkg, AppUser user) {
        Map<String, Object> body = listItem(pkg, user);
        body.put("chapters", chapters(pkg));
        body.put("versions_count", versionRepository.findActiveByPackageId(pkg.getId()).size());
        body.put("quality_notes", qualityNotes(pkg.getQualityMeta()));
        return body;
    }

    @Transactional(readOnly = true)
    public List<String> tagNames(UUID packageId) {
        List<UUID> tagIds = packageTagRepository.findAllByIdPackageId(packageId).stream()
                .map(link -> link.getId().getTagId())
                .toList();
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return tagRepository.findAllById(tagIds).stream()
                .map(Tag::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> tagViews(UUID packageId) {
        List<UUID> tagIds = packageTagRepository.findAllByIdPackageId(packageId).stream()
                .map(link -> link.getId().getTagId())
                .toList();
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return tagRepository.findAllById(tagIds).stream()
                .sorted(Comparator.comparing(Tag::getName, String.CASE_INSENSITIVE_ORDER))
                .map(tag -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("id", IdPrefix.TAG.format(tag.getId()));
                    view.put("name", tag.getName());
                    view.put("package_count", packageTagRepository.countByIdTagId(tag.getId()));
                    return view;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> collectionViews(UUID packageId) {
        List<UUID> collectionIds = packageCollectionRepository.findAllByIdPackageId(packageId).stream()
                .map(link -> link.getId().getCollectionId())
                .toList();
        if (collectionIds.isEmpty()) {
            return List.of();
        }
        return collectionRepository.findAllById(collectionIds).stream()
                .sorted(Comparator.comparingInt(CollectionEntity::getSortOrder)
                        .thenComparing(CollectionEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::collectionSummary)
                .toList();
    }

    public Map<String, Object> versionView(PackageVersion version, UUID currentVersionId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", IdPrefix.VERSION.format(version.getId()));
        body.put("version_no", version.getVersionNo());
        body.put("original_filename", version.getOriginalFilename());
        body.put("content_hash", formatHash(version.getContentHash()));
        body.put("parse_status", version.getParseStatus().name());
        body.put("parse_error", version.getParseError());
        body.put("unpacked_size", version.getUnpackedSize());
        body.put("file_count", version.getFileCount());
        body.put("is_current", version.getId().equals(currentVersionId));
        body.put("created_at", version.getCreatedAt().toString());
        return body;
    }

    private List<Map<String, Object>> chapters(KnowledgePackage pkg) {
        if (pkg.getCurrentVersionId() == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                "select id, title, order_no from extracted_document "
                        + "where package_id = ? and version_id = ? order by order_no, created_at",
                (rs, rowNum) -> {
                    Map<String, Object> chapter = new LinkedHashMap<>();
                    chapter.put("document_id", IdPrefix.DOCUMENT.format(rs.getObject("id", UUID.class)));
                    chapter.put("title", rs.getString("title"));
                    chapter.put("order_no", rs.getInt("order_no"));
                    return chapter;
                },
                pkg.getId(),
                pkg.getCurrentVersionId()
        );
    }

    private Object qualityNotes(Map<String, Object> qualityMeta) {
        if (qualityMeta == null || qualityMeta.isEmpty()) {
            return List.of();
        }
        Object notes = qualityMeta.get("quality_notes");
        if (notes instanceof List<?>) return notes;
        Object corrections = qualityMeta.get("corrections");
        return corrections instanceof List<?> ? corrections : List.of();
    }

    private Map<String, Object> collectionSummary(CollectionEntity collection) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", IdPrefix.COLLECTION.format(collection.getId()));
        view.put("name", collection.getName());
        return view;
    }

    private String formatHash(String contentHash) {
        if (contentHash == null || contentHash.startsWith("sha256:")) {
            return contentHash;
        }
        return "sha256:" + contentHash;
    }
}
