package com.kbpack.pkg;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.common.page.PageResponse;
import com.kbpack.user.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/packages")
public class PackageController {

    public record TagRequest(@NotEmpty List<@NotBlank String> tag_names) {
    }

    public record CollectionRequest(@NotBlank String collection_id) {
    }

    public static final class PatchPackageRequest {
        private boolean titlePresent;
        private String title;
        private boolean descriptionPresent;
        private String description;
        private boolean statusPresent;
        private String status;
        private boolean visibilityPresent;
        private String visibility;

        @JsonSetter("title")
        public void setTitle(String title) {
            this.titlePresent = true;
            this.title = title;
        }

        @JsonSetter("description")
        public void setDescription(String description) {
            this.descriptionPresent = true;
            this.description = description;
        }

        @JsonSetter("status")
        public void setStatus(String status) {
            this.statusPresent = true;
            this.status = status;
        }

        @JsonSetter("visibility")
        public void setVisibility(String visibility) {
            this.visibilityPresent = true;
            this.visibility = visibility;
        }

        PackageService.PatchCommand toCommand() {
            return new PackageService.PatchCommand(
                    titlePresent, title,
                    descriptionPresent, description,
                    statusPresent, status,
                    visibilityPresent, visibility
            );
        }
    }

    private final PackageQueryService queryService;
    private final PackageService packageService;
    private final PackageAccessService accessService;
    private final PackageViewService viewService;

    public PackageController(
            PackageQueryService queryService,
            PackageService packageService,
            PackageAccessService accessService,
            PackageViewService viewService
    ) {
        this.queryService = queryService;
        this.packageService = packageService;
        this.accessService = accessService;
        this.viewService = viewService;
    }

    @GetMapping
    public PageResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String collection,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        AppUser user = accessService.currentUser();
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(pageSize, 1), 100);
        Page<KnowledgePackage> result = queryService.search(
                user,
                new PackageQueryService.Filter(keyword, tag, collection, status, source, favorite),
                PageRequest.of(
                        normalizedPage - 1,
                        normalizedSize,
                        Sort.by(Sort.Direction.DESC, "updatedAt")
                )
        );
        List<Map<String, Object>> items = result.getContent().stream()
                .map(pkg -> viewService.listItem(pkg, user))
                .toList();
        return PageResponse.of(result.getTotalElements(), normalizedPage, normalizedSize, items);
    }

    @GetMapping("/{packageId}")
    public Map<String, Object> detail(@PathVariable String packageId) {
        AppUser user = accessService.currentUser();
        KnowledgePackage pkg = accessService.requireReadable(parsePackageId(packageId), user);
        return viewService.detail(pkg, user);
    }

    @PatchMapping("/{packageId}")
    public Map<String, Object> patch(
            @PathVariable String packageId,
            @RequestBody PatchPackageRequest body,
            HttpServletRequest request
    ) {
        AppUser user = accessService.currentUser();
        KnowledgePackage pkg = packageService.patch(
                parsePackageId(packageId), body.toCommand(), user, request.getRemoteAddr()
        );
        return viewService.detail(pkg, user);
    }

    @DeleteMapping("/{packageId}")
    public ResponseEntity<Void> softDelete(
            @PathVariable String packageId,
            HttpServletRequest request
    ) {
        AppUser user = accessService.currentUser();
        packageService.softDelete(parsePackageId(packageId), user, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{packageId}/archive")
    public Map<String, String> archive(
            @PathVariable String packageId,
            HttpServletRequest request
    ) {
        AppUser user = accessService.currentUser();
        packageService.archive(parsePackageId(packageId), user, request.getRemoteAddr());
        return Map.of("status", "archived");
    }

    @PostMapping("/{packageId}/favorite")
    public ResponseEntity<Void> favorite(
            @PathVariable String packageId,
            HttpServletRequest request
    ) {
        AppUser user = accessService.currentUser();
        packageService.favorite(parsePackageId(packageId), user, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{packageId}/favorite")
    public ResponseEntity<Void> unfavorite(
            @PathVariable String packageId,
            HttpServletRequest request
    ) {
        AppUser user = accessService.currentUser();
        packageService.unfavorite(parsePackageId(packageId), user, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{packageId}/tags")
    public List<Map<String, Object>> addTags(
            @PathVariable String packageId,
            @Valid @RequestBody TagRequest body,
            HttpServletRequest request
    ) {
        AppUser user = accessService.currentUser();
        UUID id = parsePackageId(packageId);
        packageService.addTags(id, body.tag_names(), user, request.getRemoteAddr());
        return viewService.tagViews(id);
    }

    @DeleteMapping("/{packageId}/tags/{tagId}")
    public ResponseEntity<Void> removeTag(
            @PathVariable String packageId,
            @PathVariable String tagId,
            HttpServletRequest request
    ) {
        AppUser user = accessService.currentUser();
        packageService.removeTag(
                parsePackageId(packageId), parseTagId(tagId), user, request.getRemoteAddr()
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{packageId}/collections")
    public List<Map<String, Object>> addCollection(
            @PathVariable String packageId,
            @Valid @RequestBody CollectionRequest body,
            HttpServletRequest request
    ) {
        AppUser user = accessService.currentUser();
        UUID id = parsePackageId(packageId);
        packageService.addCollection(
                id, parseCollectionId(body.collection_id()), user, request.getRemoteAddr()
        );
        return viewService.collectionViews(id);
    }

    @DeleteMapping("/{packageId}/collections/{collectionId}")
    public ResponseEntity<Void> removeCollection(
            @PathVariable String packageId,
            @PathVariable String collectionId,
            HttpServletRequest request
    ) {
        AppUser user = accessService.currentUser();
        packageService.removeCollection(
                parsePackageId(packageId), parseCollectionId(collectionId), user, request.getRemoteAddr()
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{packageId}/versions")
    public List<Map<String, Object>> versions(@PathVariable String packageId) {
        AppUser user = accessService.currentUser();
        UUID id = parsePackageId(packageId);
        KnowledgePackage pkg = accessService.requireReadable(id, user);
        return packageService.versions(id, user).stream()
                .map(version -> viewService.versionView(version, pkg.getCurrentVersionId()))
                .toList();
    }

    @GetMapping("/{packageId}/versions/{versionId}")
    public Map<String, Object> version(
            @PathVariable String packageId,
            @PathVariable String versionId
    ) {
        AppUser user = accessService.currentUser();
        UUID id = parsePackageId(packageId);
        KnowledgePackage pkg = accessService.requireReadable(id, user);
        PackageVersion version = packageService.version(id, parseVersionId(versionId), user);
        return viewService.versionView(version, pkg.getCurrentVersionId());
    }

    @PostMapping("/{packageId}/versions/{versionId}/set-current")
    public Map<String, String> setCurrentVersion(
            @PathVariable String packageId,
            @PathVariable String versionId,
            HttpServletRequest request
    ) {
        AppUser user = accessService.currentUser();
        UUID parsedVersionId = parseVersionId(versionId);
        packageService.setCurrentVersion(
                parsePackageId(packageId), parsedVersionId, user, request.getRemoteAddr()
        );
        return Map.of("current_version_id", IdPrefix.VERSION.format(parsedVersionId));
    }

    @DeleteMapping("/{packageId}/versions/{versionId}")
    public ResponseEntity<Void> deleteVersion(
            @PathVariable String packageId,
            @PathVariable String versionId,
            HttpServletRequest request
    ) {
        AppUser user = accessService.currentUser();
        packageService.deleteVersion(
                parsePackageId(packageId), parseVersionId(versionId), user, request.getRemoteAddr()
        );
        return ResponseEntity.noContent().build();
    }

    private UUID parsePackageId(String externalId) {
        try {
            return IdPrefix.PACKAGE.parse(externalId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.PACKAGE_NOT_FOUND);
        }
    }

    private UUID parseVersionId(String externalId) {
        try {
            return IdPrefix.VERSION.parse(externalId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VERSION_NOT_FOUND);
        }
    }

    private UUID parseTagId(String externalId) {
        try {
            return IdPrefix.TAG.parse(externalId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.NOT_FOUND, "标签不存在");
        }
    }

    private UUID parseCollectionId(String externalId) {
        try {
            return IdPrefix.COLLECTION.parse(externalId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.NOT_FOUND, "集合不存在");
        }
    }
}
