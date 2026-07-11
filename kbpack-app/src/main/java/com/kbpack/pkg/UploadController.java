package com.kbpack.pkg;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.user.AppUser;
import com.kbpack.user.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/packages")
public class UploadController {

    private final UploadService uploadService;
    private final AuthService authService;

    public UploadController(UploadService uploadService, AuthService authService) {
        this.uploadService = uploadService;
        this.authService = authService;
    }

    @PostMapping(path = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(name = "source_type", required = false) String sourceType,
            @RequestParam(name = "source_name", required = false) String sourceName,
            @RequestParam(name = "entry_file", required = false) String entryFile,
            @RequestParam(name = "target_package_id", required = false) String targetPackageId,
            @RequestParam(name = "tag_names", required = false) List<String> tagNames,
            @RequestParam(name = "collection_ids", required = false) List<String> collectionIds,
            Authentication authentication,
            HttpServletRequest request
    ) {
        AppUser user = authService.requireUserById(authentication.getPrincipal().toString());
        if (user.getRole() == AppUser.Role.viewer) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        UploadService.UploadMetadata metadata = new UploadService.UploadMetadata(
                title,
                description,
                parseSourceType(sourceType),
                sourceName,
                entryFile,
                parsePackageId(targetPackageId),
                tagNames == null ? List.of() : tagNames,
                parseCollectionIds(collectionIds),
                request.getRemoteAddr()
        );
        UploadService.UploadResult result = uploadService.upload(file, metadata, user);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("package_id", IdPrefix.PACKAGE.format(result.knowledgePackage().getId()));
        body.put("version_id", IdPrefix.VERSION.format(result.version().getId()));
        body.put("parse_status", result.version().getParseStatus().name());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    private KnowledgePackage.SourceType parseSourceType(String value) {
        if (value == null || value.isBlank()) return KnowledgePackage.SourceType.manual;
        try {
            return KnowledgePackage.SourceType.valueOf(value.toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "source_type 无效");
        }
    }

    private UUID parsePackageId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return IdPrefix.PACKAGE.parse(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.PACKAGE_NOT_FOUND);
        }
    }

    private List<UUID> parseCollectionIds(List<String> values) {
        if (values == null) return List.of();
        try {
            return values.stream().map(IdPrefix.COLLECTION::parse).distinct().toList();
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "collection_ids 包含无效 ID");
        }
    }
}
