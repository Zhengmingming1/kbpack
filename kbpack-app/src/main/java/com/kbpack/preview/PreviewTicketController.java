package com.kbpack.preview;

import com.kbpack.common.config.KbpackProperties;
import com.kbpack.admin.OperationLogService;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageVersion;
import com.kbpack.pkg.PackageVersionRepository;
import com.kbpack.user.AppUser;
import com.kbpack.user.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/packages/{packageId}/versions/{versionId}")
public class PreviewTicketController {
    private final KnowledgePackageRepository packageRepository;
    private final PackageVersionRepository versionRepository;
    private final PreviewTicketService ticketService;
    private final KbpackProperties properties;
    private final AuthService authService;
    private final OperationLogService operationLogService;

    public PreviewTicketController(
            KnowledgePackageRepository packageRepository,
            PackageVersionRepository versionRepository,
            PreviewTicketService ticketService,
            KbpackProperties properties,
            AuthService authService,
            OperationLogService operationLogService
    ) {
        this.packageRepository = packageRepository;
        this.versionRepository = versionRepository;
        this.ticketService = ticketService;
        this.properties = properties;
        this.authService = authService;
        this.operationLogService = operationLogService;
    }

    @PostMapping("/preview-ticket")
    public Map<String, Object> issue(
            @PathVariable String packageId,
            @PathVariable String versionId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        AppUser user = authService.requireUserById(authentication.getPrincipal().toString());
        UUID packageUuid = parse(packageId, IdPrefix.PACKAGE, ErrorCode.PACKAGE_NOT_FOUND);
        UUID versionUuid = parse(versionId, IdPrefix.VERSION, ErrorCode.PREVIEW_NOT_READY);
        KnowledgePackage pkg = packageRepository.findActiveById(packageUuid)
                .orElseThrow(() -> new ApiException(ErrorCode.PACKAGE_NOT_FOUND));
        boolean privileged = user.getRole() == AppUser.Role.owner || user.getRole() == AppUser.Role.admin;
        if ("private".equals(pkg.getVisibility()) && !pkg.getOwnerId().equals(user.getId()) && !privileged) {
            throw new ApiException(ErrorCode.PREVIEW_FORBIDDEN);
        }
        PackageVersion version = versionRepository.findActiveById(versionUuid)
                .filter(value -> value.getPackageId().equals(packageUuid))
                .filter(value -> value.getParseStatus() == PackageVersion.ParseStatus.success)
                .orElseThrow(() -> new ApiException(ErrorCode.PREVIEW_NOT_READY));
        if (version.getEntryFile() == null || version.getEntryFile().isBlank()) {
            throw new ApiException(ErrorCode.PREVIEW_NOT_READY, "该版本没有 HTML 入口文件");
        }
        String ticket = ticketService.issueTicket(packageId, versionId);
        String base = properties.getPreviewBaseUrl().replaceAll("/+$", "");
        String url = base + "/p/" + packageId + "/v/" + versionId + "/" + encodePath(version.getEntryFile())
                + "?ticket=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8);
        operationLogService.record(
                user.getId(),
                "preview.ticket.issue",
                "package_version",
                versionUuid,
                Map.of("package_id", packageId),
                request.getRemoteAddr());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticket", ticket);
        result.put("expires_in", ticketService.ticketTtlSeconds());
        result.put("preview_url", url);
        return result;
    }

    private UUID parse(String value, IdPrefix prefix, ErrorCode error) {
        try { return prefix.parse(value); }
        catch (IllegalArgumentException e) { throw new ApiException(error); }
    }

    private static String encodePath(String path) {
        return java.util.Arrays.stream(path.split("/"))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(java.util.stream.Collectors.joining("/"));
    }
}
