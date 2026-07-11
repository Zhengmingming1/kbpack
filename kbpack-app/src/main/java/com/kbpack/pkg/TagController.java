package com.kbpack.pkg;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    public record CreateTagRequest(@NotBlank String name) {
    }

    private final TagService tagService;
    private final PackageAccessService accessService;

    public TagController(TagService tagService, PackageAccessService accessService) {
        this.tagService = tagService;
        this.accessService = accessService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        accessService.currentUser();
        return tagService.list().stream().map(this::toView).toList();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody CreateTagRequest request,
            HttpServletRequest httpRequest
    ) {
        AppUser actor = accessService.currentUser();
        Tag tag = tagService.create(request.name(), actor, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(tag));
    }

    @DeleteMapping("/{tagId}")
    public ResponseEntity<Void> delete(
            @PathVariable String tagId,
            HttpServletRequest request
    ) {
        AppUser actor = accessService.currentUser();
        tagService.delete(parseTagId(tagId), actor, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toView(Tag tag) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", IdPrefix.TAG.format(tag.getId()));
        body.put("name", tag.getName());
        body.put("package_count", tagService.packageCount(tag.getId()));
        return body;
    }

    private UUID parseTagId(String externalId) {
        try {
            return IdPrefix.TAG.parse(externalId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.NOT_FOUND, "标签不存在");
        }
    }
}
