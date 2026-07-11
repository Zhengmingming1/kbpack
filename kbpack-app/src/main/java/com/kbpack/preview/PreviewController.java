package com.kbpack.preview;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.config.KbpackProperties;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.common.storage.ObjectStorageService;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageAsset;
import com.kbpack.pkg.PackageAssetRepository;
import com.kbpack.pkg.PackageVersion;
import com.kbpack.pkg.PackageVersionRepository;
import com.kbpack.pkg.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/p/{packageId}/v/{versionId}")
public class PreviewController {
    static final String COOKIE = "KBPACK_PREVIEW";
    private final PreviewTicketService ticketService;
    private final KnowledgePackageRepository packageRepository;
    private final PackageVersionRepository versionRepository;
    private final PackageAssetRepository assetRepository;
    private final ObjectStorageService storage;
    private final KbpackProperties properties;

    public PreviewController(
            PreviewTicketService ticketService,
            KnowledgePackageRepository packageRepository,
            PackageVersionRepository versionRepository,
            PackageAssetRepository assetRepository,
            ObjectStorageService storage,
            KbpackProperties properties
    ) {
        this.ticketService = ticketService;
        this.packageRepository = packageRepository;
        this.versionRepository = versionRepository;
        this.assetRepository = assetRepository;
        this.storage = storage;
        this.properties = properties;
    }

    @GetMapping("/{*assetPath}")
    public ResponseEntity<StreamingResponseBody> asset(
            @PathVariable String packageId,
            @PathVariable String versionId,
            @PathVariable String assetPath,
            @RequestParam(required = false) String ticket,
            @CookieValue(name = COOKIE, required = false) String session,
            HttpServletRequest request
    ) {
        if (properties.getPreview().isEnforceHost()
                && !properties.getPreview().getHost().equalsIgnoreCase(request.getServerName())) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        String cookie = null;
        if (ticket != null && !ticket.isBlank()) {
            ticketService.validateTicket(ticket, packageId, versionId);
            cookie = ticketService.issueSession(packageId, versionId);
        } else {
            ticketService.validateSession(session, packageId, versionId);
        }

        UUID packageUuid = parse(packageId, IdPrefix.PACKAGE);
        UUID versionUuid = parse(versionId, IdPrefix.VERSION);
        packageRepository.findActiveById(packageUuid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        PackageVersion version = versionRepository.findActiveById(versionUuid)
                .filter(value -> value.getPackageId().equals(packageUuid))
                .filter(value -> value.getParseStatus() == PackageVersion.ParseStatus.success)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        String rawPath = assetPath == null ? "" : assetPath.replaceFirst("^/", "");
        String path;
        try {
            path = UploadService.normalizeEntryPath(rawPath, 512);
        } catch (ApiException e) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (path.isBlank()) path = version.getEntryFile();
        PackageAsset asset = assetRepository.findByVersionIdAndPath(versionUuid, path)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        String key = packageId + "/" + versionId + "/files/" + path;
        var input = storage.open(storage.packagesBucket(), key);
        StreamingResponseBody body = output -> {
            try (input) { input.transferTo(output); }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, asset.getMimeType() == null ? "application/octet-stream" : asset.getMimeType());
        headers.setContentLength(asset.getSize());
        headers.setCacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
                + "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; "
                + "connect-src 'none'; frame-ancestors " + frameAncestor());
        if (cookie != null) {
            ResponseCookie responseCookie = ResponseCookie.from(COOKIE, cookie)
                    .httpOnly(true)
                    .secure(request.isSecure())
                    .sameSite("Lax")
                    .path("/p/" + packageId + "/v/" + versionId + "/")
                    .maxAge(Duration.ofMinutes(30))
                    .build();
            headers.add(HttpHeaders.SET_COOKIE, responseCookie.toString());
        }
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    private UUID parse(String value, IdPrefix prefix) {
        try { return prefix.parse(value); }
        catch (IllegalArgumentException e) { throw new ApiException(ErrorCode.NOT_FOUND); }
    }

    private String frameAncestor() {
        try {
            java.net.URI uri = java.net.URI.create(properties.getAppBaseUrl());
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (Exception e) {
            return "'none'";
        }
    }
}
