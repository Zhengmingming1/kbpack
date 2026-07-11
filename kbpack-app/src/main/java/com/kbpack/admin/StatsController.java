package com.kbpack.admin;

import com.kbpack.pkg.PackageAccessService;
import com.kbpack.user.AppUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class StatsController {

    private final StatsService statsService;
    private final PackageAccessService accessService;

    public StatsController(StatsService statsService, PackageAccessService accessService) {
        this.statsService = statsService;
        this.accessService = accessService;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        AppUser user = accessService.currentUser();
        return statsService.stats(user);
    }
}
