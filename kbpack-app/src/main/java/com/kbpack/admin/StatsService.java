package com.kbpack.admin;

import com.kbpack.common.id.IdPrefix;
import com.kbpack.pkg.PackageAccessService;
import com.kbpack.user.AppUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StatsService {

    private static final String ACCESS_PREDICATE =
            " and (? or p.owner_id = ? or p.visibility in ('team', 'public'))";

    private final JdbcTemplate jdbcTemplate;
    private final PackageAccessService accessService;

    public StatsService(JdbcTemplate jdbcTemplate, PackageAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats(AppUser user) {
        boolean administrator = accessService.isAdministrator(user);
        UUID userId = user.getId();

        long packageCount = number(
                "select count(*) from knowledge_package p where p.deleted_at is null" + ACCESS_PREDICATE,
                administrator, userId
        );
        long documentCount = number(
                "select count(*) from extracted_document d "
                        + "join knowledge_package p on p.id = d.package_id "
                        + "where p.deleted_at is null" + ACCESS_PREDICATE,
                administrator, userId
        );
        long storageUsed = number(
                "select coalesce(sum(v.unpacked_size), 0) from package_version v "
                        + "join knowledge_package p on p.id = v.package_id "
                        + "where v.deleted_at is null and p.deleted_at is null" + ACCESS_PREDICATE,
                administrator, userId
        );
        long parseFailed = number(
                "select count(*) from parse_task t "
                        + "join package_version v on v.id = t.version_id "
                        + "join knowledge_package p on p.id = v.package_id "
                        + "where t.status = 'failed' and v.deleted_at is null and p.deleted_at is null"
                        + ACCESS_PREDICATE,
                administrator, userId
        );

        List<Map<String, Object>> recentUploads = jdbcTemplate.query(
                "select p.id, p.title, p.created_at from knowledge_package p "
                        + "where p.deleted_at is null" + ACCESS_PREDICATE
                        + " order by p.created_at desc limit 5",
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("package_id", IdPrefix.PACKAGE.format(rs.getObject("id", UUID.class)));
                    item.put("title", rs.getString("title"));
                    item.put("created_at", rs.getTimestamp("created_at").toInstant().toString());
                    return item;
                },
                administrator,
                userId
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("package_count", packageCount);
        body.put("document_count", documentCount);
        body.put("storage_used_bytes", storageUsed);
        body.put("parse_failed_count", parseFailed);
        body.put("recent_uploads", recentUploads);
        return body;
    }

    private long number(String sql, boolean administrator, UUID userId) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, administrator, userId);
        return value == null ? 0L : value.longValue();
    }
}
