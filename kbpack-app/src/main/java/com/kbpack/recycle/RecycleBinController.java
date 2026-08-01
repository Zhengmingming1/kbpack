package com.kbpack.recycle;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.common.page.PageResponse;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.PackageAccessService;
import com.kbpack.pkg.PackageViewService;
import com.kbpack.user.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trash/packages")
public class RecycleBinController {

    private final RecycleBinService recycleBinService;
    private final PackageAccessService accessService;
    private final PackageViewService viewService;

    public RecycleBinController(
            RecycleBinService recycleBinService,
            PackageAccessService accessService,
            PackageViewService viewService
    ) {
        this.recycleBinService = recycleBinService;
        this.accessService = accessService;
        this.viewService = viewService;
    }

    @GetMapping
    public PageResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        AppUser user = accessService.currentUser();
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(pageSize, 1), 100);
        Page<RecycleBinService.DeletedPackage> result = recycleBinService.list(
                user,
                PageRequest.of(
                        normalizedPage - 1,
                        normalizedSize,
                        Sort.by(Sort.Direction.DESC, "deletedAt")
                )
        );
        List<Map<String, Object>> items = result.getContent().stream()
                .map(item -> deletedPackageView(item, user))
                .toList();
        return PageResponse.of(result.getTotalElements(), normalizedPage, normalizedSize, items);
    }

    @PostMapping("/{packageId}/restore")
    public Map<String, Object> restore(
            @PathVariable String packageId,
            HttpServletRequest request
    ) {
        AppUser user = accessService.currentUser();
        KnowledgePackage restored = recycleBinService.restore(
                parsePackageId(packageId), user, request.getRemoteAddr()
        );
        return viewService.listItem(restored, user);
    }

    @DeleteMapping("/{packageId}")
    public ResponseEntity<Void> purge(
            @PathVariable String packageId,
            HttpServletRequest request
    ) {
        AppUser user = accessService.currentUser();
        recycleBinService.purge(parsePackageId(packageId), user, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> deletedPackageView(
            RecycleBinService.DeletedPackage deleted,
            AppUser user
    ) {
        Map<String, Object> view = new LinkedHashMap<>(viewService.listItem(deleted.pkg(), user));
        view.put("deleted_at", deleted.pkg().getDeletedAt());
        view.put("purge_at", deleted.purgeAt());
        view.put("can_restore", deleted.canRestore());
        view.put("can_delete", true);
        view.put("versions_count", deleted.versionsCount());
        return view;
    }

    private UUID parsePackageId(String externalId) {
        try {
            return IdPrefix.PACKAGE.parse(externalId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.PACKAGE_NOT_FOUND);
        }
    }
}
