package com.kbpack.pkg;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.user.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/collections")
public class CollectionController {

    public record CreateCollectionRequest(
            @NotBlank String name,
            String parent_id,
            Integer sort_order
    ) {
    }

    public static final class PatchCollectionRequest {
        private boolean namePresent;
        private String name;
        private boolean parentPresent;
        private String parentId;
        private boolean sortOrderPresent;
        private Integer sortOrder;

        @JsonSetter("name")
        public void setName(String name) {
            this.namePresent = true;
            this.name = name;
        }

        @JsonSetter("parent_id")
        public void setParentId(String parentId) {
            this.parentPresent = true;
            this.parentId = parentId;
        }

        @JsonSetter("sort_order")
        public void setSortOrder(Integer sortOrder) {
            this.sortOrderPresent = true;
            this.sortOrder = sortOrder;
        }
    }

    private static final Comparator<CollectionEntity> COLLECTION_ORDER = Comparator
            .comparingInt(CollectionEntity::getSortOrder)
            .thenComparing(CollectionEntity::getCreatedAt);

    private final CollectionService collectionService;
    private final PackageAccessService accessService;

    public CollectionController(
            CollectionService collectionService,
            PackageAccessService accessService
    ) {
        this.collectionService = collectionService;
        this.accessService = accessService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        accessService.currentUser();
        return toTree(collectionService.list());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody CreateCollectionRequest request,
            HttpServletRequest httpRequest
    ) {
        AppUser actor = accessService.currentUser();
        CollectionEntity collection = collectionService.create(
                request.name(), parseNullableCollectionId(request.parent_id()), request.sort_order(),
                actor, httpRequest.getRemoteAddr()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(collection, List.of()));
    }

    @PatchMapping("/{collectionId}")
    public Map<String, Object> patch(
            @PathVariable String collectionId,
            @RequestBody PatchCollectionRequest request,
            HttpServletRequest httpRequest
    ) {
        AppUser actor = accessService.currentUser();
        CollectionEntity collection = collectionService.patch(
                parseCollectionId(collectionId),
                new CollectionService.Patch(
                        request.namePresent,
                        request.name,
                        request.parentPresent,
                        request.parentPresent ? parseNullableCollectionId(request.parentId) : null,
                        request.sortOrderPresent,
                        request.sortOrder
                ),
                actor,
                httpRequest.getRemoteAddr()
        );
        return toView(collection, List.of());
    }

    @DeleteMapping("/{collectionId}")
    public ResponseEntity<Void> delete(
            @PathVariable String collectionId,
            HttpServletRequest request
    ) {
        AppUser actor = accessService.currentUser();
        collectionService.delete(parseCollectionId(collectionId), actor, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    private List<Map<String, Object>> toTree(List<CollectionEntity> collections) {
        Map<UUID, List<CollectionEntity>> childrenByParent = new HashMap<>();
        for (CollectionEntity collection : collections) {
            childrenByParent.computeIfAbsent(collection.getParentId(), ignored -> new ArrayList<>())
                    .add(collection);
        }
        childrenByParent.values().forEach(children -> children.sort(COLLECTION_ORDER));
        return childrenByParent.getOrDefault(null, List.of()).stream()
                .map(root -> renderNode(root, childrenByParent, new HashSet<>()))
                .toList();
    }

    private Map<String, Object> renderNode(
            CollectionEntity collection,
            Map<UUID, List<CollectionEntity>> childrenByParent,
            Set<UUID> path
    ) {
        if (!path.add(collection.getId())) {
            return toView(collection, List.of());
        }
        List<Map<String, Object>> children = childrenByParent
                .getOrDefault(collection.getId(), List.of())
                .stream()
                .map(child -> renderNode(child, childrenByParent, new HashSet<>(path)))
                .toList();
        return toView(collection, children);
    }

    private Map<String, Object> toView(
            CollectionEntity collection,
            List<Map<String, Object>> children
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", IdPrefix.COLLECTION.format(collection.getId()));
        body.put("name", collection.getName());
        body.put("parent_id", collection.getParentId() == null
                ? null
                : IdPrefix.COLLECTION.format(collection.getParentId()));
        body.put("sort_order", collection.getSortOrder());
        body.put("children", children);
        return body;
    }

    private UUID parseNullableCollectionId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return null;
        }
        return parseCollectionId(externalId);
    }

    private UUID parseCollectionId(String externalId) {
        try {
            return IdPrefix.COLLECTION.parse(externalId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.NOT_FOUND, "集合不存在");
        }
    }
}
