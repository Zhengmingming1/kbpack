package com.kbpack.pkg;

import com.kbpack.admin.OperationLogService;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.search.SearchIndexUpdateCoordinator;
import com.kbpack.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CollectionService {

    public record Patch(
            boolean namePresent,
            String name,
            boolean parentPresent,
            UUID parentId,
            boolean sortOrderPresent,
            Integer sortOrder
    ) {
    }

    private final CollectionRepository collectionRepository;
    private final PackageAccessService accessService;
    private final OperationLogService operationLogService;
    private final PackageCollectionRepository packageCollectionRepository;
    private final SearchIndexUpdateCoordinator searchIndexUpdates;

    public CollectionService(
            CollectionRepository collectionRepository,
            PackageAccessService accessService,
            OperationLogService operationLogService,
            PackageCollectionRepository packageCollectionRepository,
            SearchIndexUpdateCoordinator searchIndexUpdates
    ) {
        this.collectionRepository = collectionRepository;
        this.accessService = accessService;
        this.operationLogService = operationLogService;
        this.packageCollectionRepository = packageCollectionRepository;
        this.searchIndexUpdates = searchIndexUpdates;
    }

    @Transactional(readOnly = true)
    public List<CollectionEntity> list() {
        return collectionRepository.findAllByOrderBySortOrderAscCreatedAtAsc();
    }

    @Transactional
    public CollectionEntity create(
            String rawName,
            UUID parentId,
            Integer sortOrder,
            AppUser actor,
            String ip
    ) {
        accessService.requireContentWriter(actor);
        requireParent(parentId);
        CollectionEntity collection = new CollectionEntity();
        collection.setName(normalizeName(rawName));
        collection.setParentId(parentId);
        if (sortOrder != null) {
            collection.setSortOrder(sortOrder);
        }
        collectionRepository.save(collection);
        operationLogService.record(
                actor.getId(), "collection.create", "collection", collection.getId(),
                Map.of("name", collection.getName()), ip
        );
        return collection;
    }

    @Transactional
    public CollectionEntity patch(
            UUID collectionId,
            Patch patch,
            AppUser actor,
            String ip
    ) {
        accessService.requireContentWriter(actor);
        CollectionEntity collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "集合不存在"));
        if (!patch.namePresent() && !patch.parentPresent() && !patch.sortOrderPresent()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "至少需要提供一个可更新字段");
        }
        if (patch.namePresent()) {
            collection.setName(normalizeName(patch.name()));
        }
        if (patch.parentPresent()) {
            validateParentChange(collectionId, patch.parentId());
            collection.setParentId(patch.parentId());
        }
        if (patch.sortOrderPresent()) {
            if (patch.sortOrder() == null) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "sort_order 不能为空");
            }
            collection.setSortOrder(patch.sortOrder());
        }
        collectionRepository.save(collection);
        operationLogService.record(
                actor.getId(), "collection.update", "collection", collectionId, Map.of(), ip
        );
        return collection;
    }

    @Transactional
    public void delete(UUID collectionId, AppUser actor, String ip) {
        accessService.requireContentWriter(actor);
        CollectionEntity collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "集合不存在"));
        Set<UUID> subtree = collectionSubtree(collectionId);
        List<UUID> affectedPackages = packageCollectionRepository.findAllByIdCollectionIdIn(subtree).stream()
                .map(link -> link.getId().getPackageId()).toList();
        collectionRepository.delete(collection);
        operationLogService.record(
                actor.getId(), "collection.delete", "collection", collectionId,
                Map.of("name", collection.getName()), ip
        );
        searchIndexUpdates.refreshPackagesAfterCommit(affectedPackages);
    }

    private Set<UUID> collectionSubtree(UUID rootId) {
        Set<UUID> subtree = new LinkedHashSet<>();
        subtree.add(rootId);
        List<CollectionEntity> collections = collectionRepository.findAll();
        boolean changed;
        do {
            changed = false;
            for (CollectionEntity candidate : collections) {
                if (candidate.getParentId() != null && subtree.contains(candidate.getParentId())) {
                    changed |= subtree.add(candidate.getId());
                }
            }
        } while (changed);
        return subtree;
    }

    private void validateParentChange(UUID collectionId, UUID parentId) {
        if (parentId == null) {
            return;
        }
        UUID cursor = parentId;
        Set<UUID> visited = new HashSet<>();
        while (cursor != null) {
            if (collectionId.equals(cursor)) {
                throw new ApiException(ErrorCode.CONFLICT, "集合层级不能形成循环");
            }
            if (!visited.add(cursor)) {
                throw new ApiException(ErrorCode.CONFLICT, "集合层级存在循环");
            }
            CollectionEntity parent = collectionRepository.findById(cursor)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "父集合不存在"));
            cursor = parent.getParentId();
        }
    }

    private void requireParent(UUID parentId) {
        if (parentId != null && !collectionRepository.existsById(parentId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "父集合不存在");
        }
    }

    private String normalizeName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "集合名称不能为空");
        }
        String name = rawName.trim();
        if (name.length() > 128) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "集合名称不能超过 128 字符");
        }
        return name;
    }
}
