package com.kbpack.task;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.common.page.PageResponse;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageVersion;
import com.kbpack.pkg.PackageVersionRepository;
import com.kbpack.user.AppUser;
import com.kbpack.user.AuthService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
public class ParseTaskController {
    private final ParseTaskRepository taskRepository;
    private final ParseTaskStateService stateService;
    private final PackageVersionRepository versionRepository;
    private final KnowledgePackageRepository packageRepository;
    private final AuthService authService;

    public ParseTaskController(
            ParseTaskRepository taskRepository,
            ParseTaskStateService stateService,
            PackageVersionRepository versionRepository,
            KnowledgePackageRepository packageRepository,
            AuthService authService
    ) {
        this.taskRepository = taskRepository;
        this.stateService = stateService;
        this.versionRepository = versionRepository;
        this.packageRepository = packageRepository;
        this.authService = authService;
    }

    @GetMapping
    public PageResponse<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(name = "version_id", required = false) String versionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            Authentication authentication
    ) {
        int currentPage = Math.max(1, page);
        int size = Math.min(100, Math.max(1, pageSize));
        var pageable = PageRequest.of(currentPage - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        ParseTask.Status parsedStatus = parseStatus(status);
        UUID parsedVersion = parseExternalId(versionId, IdPrefix.VERSION);
        AppUser user = currentUser(authentication);
        Specification<ParseTask> specification = (root, query, cb) -> {
            var version = query.from(PackageVersion.class);
            var pkg = query.from(KnowledgePackage.class);
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("versionId"), version.get("id")));
            predicates.add(cb.equal(version.get("packageId"), pkg.get("id")));
            predicates.add(cb.isNull(version.get("deletedAt")));
            predicates.add(cb.isNull(pkg.get("deletedAt")));
            if (parsedStatus != null) predicates.add(cb.equal(root.get("status"), parsedStatus));
            if (parsedVersion != null) predicates.add(cb.equal(root.get("versionId"), parsedVersion));
            if (!isAdministrator(user)) {
                predicates.add(cb.or(cb.equal(pkg.get("ownerId"), user.getId()),
                        pkg.get("visibility").in("team", "public")));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<ParseTask> result = taskRepository.findAll(specification, pageable);
        return PageResponse.of(result.getTotalElements(), currentPage, size, result.stream().map(this::view).toList());
    }

    @GetMapping("/{taskId}")
    public Map<String, Object> detail(@PathVariable String taskId, Authentication authentication) {
        ParseTask task = taskRepository.findById(parseRequired(taskId, IdPrefix.TASK))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "解析任务不存在"));
        requireReadable(task, currentUser(authentication));
        return view(task);
    }

    @PostMapping("/{taskId}/retry")
    public ResponseEntity<Map<String, String>> retry(@PathVariable String taskId, Authentication authentication) {
        UUID id = parseRequired(taskId, IdPrefix.TASK);
        ParseTask existing = taskRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "解析任务不存在"));
        AppUser user = currentUser(authentication);
        KnowledgePackage pkg = requireReadable(existing, user);
        boolean editorOwner = user.getRole() == AppUser.Role.editor && pkg.getOwnerId().equals(user.getId());
        if (!isAdministrator(user) && !editorOwner) throw new ApiException(ErrorCode.FORBIDDEN);
        ParseTask task = stateService.retry(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", task.getStatus().name()));
    }

    private Map<String, Object> view(ParseTask task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", IdPrefix.TASK.format(task.getId()));
        result.put("version_id", IdPrefix.VERSION.format(task.getVersionId()));
        result.put("task_type", task.getTaskType().name());
        result.put("status", task.getStatus().name());
        result.put("attempt_count", task.getAttemptCount());
        result.put("max_attempts", task.getMaxAttempts());
        result.put("error_message", task.getErrorMessage());
        result.put("created_at", task.getCreatedAt());
        result.put("started_at", task.getStartedAt());
        result.put("finished_at", task.getFinishedAt());
        return result;
    }

    private ParseTask.Status parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try { return ParseTask.Status.valueOf(value.toLowerCase()); }
        catch (IllegalArgumentException e) { throw new ApiException(ErrorCode.BAD_REQUEST, "status 无效"); }
    }

    private UUID parseExternalId(String value, IdPrefix prefix) {
        if (value == null || value.isBlank()) return null;
        return parseRequired(value, prefix);
    }

    private UUID parseRequired(String value, IdPrefix prefix) {
        try { return prefix.parse(value); }
        catch (IllegalArgumentException e) { throw new ApiException(ErrorCode.NOT_FOUND, "资源不存在"); }
    }

    private AppUser currentUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return authService.requireUserById(authentication.getPrincipal().toString());
    }

    private KnowledgePackage requireReadable(ParseTask task, AppUser user) {
        PackageVersion version = versionRepository.findActiveById(task.getVersionId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "解析任务不存在"));
        KnowledgePackage pkg = packageRepository.findActiveById(version.getPackageId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "解析任务不存在"));
        if (!isAdministrator(user) && !pkg.getOwnerId().equals(user.getId())
                && "private".equals(pkg.getVisibility())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "解析任务不存在");
        }
        return pkg;
    }

    private boolean isAdministrator(AppUser user) {
        return user.getRole() == AppUser.Role.owner || user.getRole() == AppUser.Role.admin;
    }
}
