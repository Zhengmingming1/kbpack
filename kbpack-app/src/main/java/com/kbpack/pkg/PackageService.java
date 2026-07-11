package com.kbpack.pkg;

import com.kbpack.admin.OperationLogService;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.user.AppUser;
import com.kbpack.search.SearchIndexService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PackageService {

    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> VISIBILITIES = Set.of("private", "team", "public");

    public record PatchCommand(
            boolean titlePresent,
            String title,
            boolean descriptionPresent,
            String description,
            boolean statusPresent,
            String status,
            boolean visibilityPresent,
            String visibility
    ) {
    }

    private final KnowledgePackageRepository packageRepository;
    private final PackageVersionRepository versionRepository;
    private final TagRepository tagRepository;
    private final PackageTagRepository packageTagRepository;
    private final CollectionRepository collectionRepository;
    private final PackageCollectionRepository packageCollectionRepository;
    private final FavoriteRepository favoriteRepository;
    private final PackageAccessService accessService;
    private final OperationLogService operationLogService;
    private final SearchIndexService searchIndexService;

    public PackageService(
            KnowledgePackageRepository packageRepository,
            PackageVersionRepository versionRepository,
            TagRepository tagRepository,
            PackageTagRepository packageTagRepository,
            CollectionRepository collectionRepository,
            PackageCollectionRepository packageCollectionRepository,
            FavoriteRepository favoriteRepository,
            PackageAccessService accessService,
            OperationLogService operationLogService,
            SearchIndexService searchIndexService
    ) {
        this.packageRepository = packageRepository;
        this.versionRepository = versionRepository;
        this.tagRepository = tagRepository;
        this.packageTagRepository = packageTagRepository;
        this.collectionRepository = collectionRepository;
        this.packageCollectionRepository = packageCollectionRepository;
        this.favoriteRepository = favoriteRepository;
        this.accessService = accessService;
        this.operationLogService = operationLogService;
        this.searchIndexService = searchIndexService;
    }

    @Transactional
    public KnowledgePackage createDraft(
            String title,
            String description,
            KnowledgePackage.SourceType sourceType,
            String sourceName,
            UUID ownerId
    ) {
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setTitle(normalizeTitle(title));
        pkg.setSlug(uniqueSlug(pkg.getTitle()));
        pkg.setDescription(description);
        pkg.setSourceType(sourceType == null ? KnowledgePackage.SourceType.manual : sourceType);
        pkg.setSourceName(sourceName);
        pkg.setStatus(KnowledgePackage.Status.draft);
        pkg.setVisibility("private");
        pkg.setOwnerId(ownerId);
        packageRepository.save(pkg);
        operationLogService.record(
                ownerId,
                "package.create",
                "knowledge_package",
                pkg.getId(),
                Map.of("source_type", pkg.getSourceType().name()),
                null
        );
        return pkg;
    }

    @Transactional
    public KnowledgePackage patch(
            UUID packageId,
            PatchCommand command,
            AppUser actor,
            String ip
    ) {
        KnowledgePackage pkg = accessService.requireWritable(packageId, actor);
        if (!command.titlePresent() && !command.descriptionPresent()
                && !command.statusPresent() && !command.visibilityPresent()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "至少需要提供一个可更新字段");
        }
        if (command.titlePresent()) {
            if (command.title() == null || command.title().isBlank()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "title 不能为空");
            }
            pkg.setTitle(normalizeTitle(command.title()));
        }
        if (command.descriptionPresent()) {
            if (command.description() != null && command.description().length() > 10_000) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "description 不能超过 10000 字符");
            }
            pkg.setDescription(command.description());
        }
        if (command.statusPresent()) {
            KnowledgePackage.Status nextStatus = parseStatus(command.status());
            validateStatusTransition(pkg.getStatus(), nextStatus);
            pkg.setStatus(nextStatus);
        }
        if (command.visibilityPresent()) {
            pkg.setVisibility(parseVisibility(command.visibility()));
        }
        packageRepository.save(pkg);
        operationLogService.record(
                actor.getId(), "package.update", "knowledge_package", pkg.getId(), Map.of(), ip
        );
        return pkg;
    }

    @Transactional
    public KnowledgePackage softDelete(UUID id) {
        KnowledgePackage pkg = packageRepository.findActiveById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.PACKAGE_NOT_FOUND));
        Instant deletedAt = Instant.now();
        pkg.setDeletedAt(deletedAt);
        versionRepository.findActiveByPackageId(id).forEach(version -> {
            version.setDeletedAt(deletedAt);
            removeFromIndex(version.getId());
        });
        return packageRepository.save(pkg);
    }

    @Transactional
    public void softDelete(UUID id, AppUser actor, String ip) {
        accessService.requireWritable(id, actor);
        softDelete(id);
        operationLogService.record(
                actor.getId(), "package.delete", "knowledge_package", id, Map.of(), ip
        );
    }

    @Transactional
    public KnowledgePackage archive(UUID id) {
        KnowledgePackage pkg = packageRepository.findActiveById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.PACKAGE_NOT_FOUND));
        validateStatusTransition(pkg.getStatus(), KnowledgePackage.Status.archived);
        pkg.setStatus(KnowledgePackage.Status.archived);
        return packageRepository.save(pkg);
    }

    @Transactional
    public KnowledgePackage archive(UUID id, AppUser actor, String ip) {
        accessService.requireWritable(id, actor);
        KnowledgePackage pkg = archive(id);
        operationLogService.record(
                actor.getId(), "package.archive", "knowledge_package", id, Map.of(), ip
        );
        return pkg;
    }

    @Transactional
    public void favorite(UUID packageId, AppUser actor, String ip) {
        accessService.requireReadable(packageId, actor);
        if (!favoriteRepository.existsByIdUserIdAndIdPackageId(actor.getId(), packageId)) {
            favoriteRepository.save(new Favorite(new FavoriteId(actor.getId(), packageId)));
            operationLogService.record(
                    actor.getId(), "package.favorite", "knowledge_package", packageId, Map.of(), ip
            );
        }
    }

    @Transactional
    public void unfavorite(UUID packageId, AppUser actor, String ip) {
        accessService.requireReadable(packageId, actor);
        favoriteRepository.deleteByIdUserIdAndIdPackageId(actor.getId(), packageId);
        operationLogService.record(
                actor.getId(), "package.unfavorite", "knowledge_package", packageId, Map.of(), ip
        );
    }

    @Transactional
    public List<Tag> addTags(
            UUID packageId,
            List<String> tagNames,
            AppUser actor,
            String ip
    ) {
        accessService.requireWritable(packageId, actor);
        if (tagNames == null || tagNames.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "tag_names 不能为空");
        }
        LinkedHashSet<String> normalizedNames = new LinkedHashSet<>();
        for (String rawName : tagNames) {
            String name = normalizeTagName(rawName);
            normalizedNames.add(name);
        }

        List<Tag> linked = new ArrayList<>();
        for (String name : normalizedNames) {
            Tag tag = tagRepository.findByName(name).orElseGet(() -> {
                Tag created = new Tag();
                created.setName(name);
                return tagRepository.save(created);
            });
            if (!packageTagRepository.existsByIdPackageIdAndIdTagId(packageId, tag.getId())) {
                packageTagRepository.save(new PackageTag(new PackageTagId(packageId, tag.getId())));
            }
            linked.add(tag);
        }
        operationLogService.record(
                actor.getId(), "package.tags.add", "knowledge_package", packageId,
                Map.of("tag_names", normalizedNames), ip
        );
        return linked;
    }

    @Transactional
    public void removeTag(UUID packageId, UUID tagId, AppUser actor, String ip) {
        accessService.requireWritable(packageId, actor);
        if (!tagRepository.existsById(tagId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "标签不存在");
        }
        packageTagRepository.deleteByIdPackageIdAndIdTagId(packageId, tagId);
        operationLogService.record(
                actor.getId(), "package.tags.remove", "knowledge_package", packageId,
                Map.of("tag_id", tagId.toString()), ip
        );
    }

    @Transactional
    public List<CollectionEntity> addCollection(
            UUID packageId,
            UUID collectionId,
            AppUser actor,
            String ip
    ) {
        accessService.requireWritable(packageId, actor);
        CollectionEntity collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "集合不存在"));
        if (!packageCollectionRepository.existsByIdPackageIdAndIdCollectionId(packageId, collectionId)) {
            packageCollectionRepository.save(
                    new PackageCollection(new PackageCollectionId(packageId, collectionId))
            );
        }
        operationLogService.record(
                actor.getId(), "package.collections.add", "knowledge_package", packageId,
                Map.of("collection_id", collectionId.toString()), ip
        );
        return List.of(collection);
    }

    @Transactional
    public void removeCollection(UUID packageId, UUID collectionId, AppUser actor, String ip) {
        accessService.requireWritable(packageId, actor);
        if (!collectionRepository.existsById(collectionId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "集合不存在");
        }
        packageCollectionRepository.deleteByIdPackageIdAndIdCollectionId(packageId, collectionId);
        operationLogService.record(
                actor.getId(), "package.collections.remove", "knowledge_package", packageId,
                Map.of("collection_id", collectionId.toString()), ip
        );
    }

    @Transactional(readOnly = true)
    public List<PackageVersion> versions(UUID packageId, AppUser actor) {
        accessService.requireReadable(packageId, actor);
        return versionRepository.findActiveByPackageId(packageId);
    }

    @Transactional(readOnly = true)
    public PackageVersion version(UUID packageId, UUID versionId, AppUser actor) {
        accessService.requireReadable(packageId, actor);
        return versionRepository.findActiveByIdAndPackageId(versionId, packageId)
                .orElseThrow(() -> new ApiException(ErrorCode.VERSION_NOT_FOUND));
    }

    @Transactional
    public KnowledgePackage setCurrentVersion(
            UUID packageId,
            UUID versionId,
            AppUser actor,
            String ip
    ) {
        KnowledgePackage pkg = accessService.requireWritable(packageId, actor);
        versionRepository.findActiveByIdAndPackageId(versionId, packageId)
                .orElseThrow(() -> new ApiException(ErrorCode.VERSION_NOT_FOUND));
        pkg.setCurrentVersionId(versionId);
        packageRepository.save(pkg);
        operationLogService.record(
                actor.getId(), "version.set_current", "package_version", versionId,
                Map.of("package_id", packageId.toString()), ip
        );
        return pkg;
    }

    @Transactional
    public void deleteVersion(
            UUID packageId,
            UUID versionId,
            AppUser actor,
            String ip
    ) {
        KnowledgePackage pkg = accessService.requireWritable(packageId, actor);
        PackageVersion version = versionRepository.findActiveByIdAndPackageId(versionId, packageId)
                .orElseThrow(() -> new ApiException(ErrorCode.VERSION_NOT_FOUND));
        if (versionId.equals(pkg.getCurrentVersionId())) {
            throw new ApiException(ErrorCode.CURRENT_VERSION_DELETE_FORBIDDEN);
        }
        version.setDeletedAt(Instant.now());
        versionRepository.save(version);
        removeFromIndex(versionId);
        operationLogService.record(
                actor.getId(), "version.delete", "package_version", versionId,
                Map.of("package_id", packageId.toString()), ip
        );
    }

    private void removeFromIndex(UUID versionId) {
        if (searchIndexService == null) return;
        try {
            searchIndexService.deleteVersion(versionId);
        } catch (RuntimeException ignored) {
            // PostgreSQL is authoritative; reindex and retention cleanup repair transient search failures.
        }
    }

    public String uniqueSlug(String title) {
        String base = toSlug(title);
        if (base.isBlank()) {
            base = "package";
        }
        String candidate = base;
        int i = 1;
        while (packageRepository.existsBySlug(candidate)) {
            candidate = base + "-" + (++i);
        }
        return candidate;
    }

    static String toSlug(String input) {
        String n = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFKD);
        n = n.toLowerCase(Locale.ROOT);
        n = NON_SLUG.matcher(n).replaceAll("-");
        n = n.replaceAll("^-+|-+$", "");
        if (n.length() > 80) {
            n = n.substring(0, 80);
        }
        return n;
    }

    private String normalizeTitle(String title) {
        String normalized = title == null || title.isBlank() ? "未命名知识包" : title.trim();
        if (normalized.length() > 256) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "title 不能超过 256 字符");
        }
        return normalized;
    }

    private void validateStatusTransition(KnowledgePackage.Status current, KnowledgePackage.Status next) {
        if (current == next) return;
        boolean allowed = switch (current) {
            case draft -> next == KnowledgePackage.Status.active;
            case active -> next == KnowledgePackage.Status.deprecated
                    || next == KnowledgePackage.Status.archived;
            case deprecated -> next == KnowledgePackage.Status.archived;
            case archived -> false;
        };
        if (!allowed) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "不允许将知识包状态从 " + current + " 变更为 " + next);
        }
    }

    private String normalizeTagName(String tagName) {
        if (tagName == null || tagName.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "标签名不能为空");
        }
        String normalized = tagName.trim();
        if (normalized.length() > 64) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "标签名不能超过 64 字符");
        }
        return normalized;
    }

    private KnowledgePackage.Status parseStatus(String value) {
        if (value == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "status 不能为空");
        }
        try {
            return KnowledgePackage.Status.valueOf(value.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "status 参数无效");
        }
    }

    private String parseVisibility(String value) {
        if (value == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "visibility 不能为空");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!VISIBILITIES.contains(normalized)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "visibility 参数无效");
        }
        return normalized;
    }
}
