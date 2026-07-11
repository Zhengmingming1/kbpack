package com.kbpack.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.kbpack.user.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
public class SystemSettingController {

    private final AdminAccessService accessService;
    private final SystemSettingService settingService;

    public SystemSettingController(
            AdminAccessService accessService,
            SystemSettingService settingService
    ) {
        this.accessService = accessService;
        this.settingService = settingService;
    }

    @GetMapping
    public Map<String, JsonNode> getAll() {
        accessService.requireAdministrator();
        return settingService.getAll();
    }

    @PatchMapping
    public Map<String, JsonNode> patch(
            @RequestBody Map<String, JsonNode> updates,
            HttpServletRequest request
    ) {
        AppUser actor = accessService.requireAdministrator();
        return settingService.patch(updates, actor, request.getRemoteAddr());
    }
}
