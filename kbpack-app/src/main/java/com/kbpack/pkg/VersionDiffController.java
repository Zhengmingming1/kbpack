package com.kbpack.pkg;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.user.AppUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/packages/{packageId}/versions")
public class VersionDiffController {

    private final PackageAccessService accessService;
    private final VersionDiffService diffService;

    public VersionDiffController(
            PackageAccessService accessService,
            VersionDiffService diffService
    ) {
        this.accessService = accessService;
        this.diffService = diffService;
    }

    @GetMapping("/diff")
    public VersionDiffService.VersionDiffResponse compare(
            @PathVariable String packageId,
            @RequestParam(name = "base_version_id") String baseVersionId,
            @RequestParam(name = "target_version_id") String targetVersionId
    ) {
        AppUser user = accessService.currentUser();
        return diffService.compare(
                parsePackageId(packageId),
                parseVersionId(baseVersionId),
                parseVersionId(targetVersionId),
                user
        );
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
}
