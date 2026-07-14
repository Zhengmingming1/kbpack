package com.kbpack.pkg;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
class VersionDiffAssetRepository {

    record AssetSnapshot(
            String path,
            String mimeType,
            long size,
            String sha256,
            String role
    ) {
    }

    record AssetPair(
            String path,
            AssetSnapshot base,
            AssetSnapshot target
    ) {
    }

    private static final String ASSET_PAIRS_SQL = """
            with base_assets as (
                select distinct on (path)
                       path, mime_type, size, sha256, role
                  from package_asset
                 where version_id = ?
                 order by path, id
                 limit ?
            ), target_assets as (
                select distinct on (path)
                       path, mime_type, size, sha256, role
                  from package_asset
                 where version_id = ?
                 order by path, id
                 limit ?
            )
            select coalesce(b.path, t.path) as path,
                   b.path is not null as base_present,
                   b.mime_type as base_mime_type,
                   b.size as base_size,
                   b.sha256 as base_sha256,
                   b.role as base_role,
                   t.path is not null as target_present,
                   t.mime_type as target_mime_type,
                   t.size as target_size,
                   t.sha256 as target_sha256,
                   t.role as target_role
              from base_assets b
              full outer join target_assets t on t.path = b.path
             order by path
             limit ?
            """;

    private static final String DISTINCT_PATH_COUNT_SQL = """
            select count(*)
              from (
                    select path from package_asset where version_id = ?
                    union
                    select path from package_asset where version_id = ?
                   ) assets
            """;

    private final JdbcTemplate jdbcTemplate;

    VersionDiffAssetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<AssetPair> loadAssetPairs(UUID baseVersionId, UUID targetVersionId, int limit) {
        return jdbcTemplate.query(
                ASSET_PAIRS_SQL,
                (rs, rowNum) -> new AssetPair(
                        rs.getString("path"),
                        snapshot(rs, "base_", rs.getBoolean("base_present")),
                        snapshot(rs, "target_", rs.getBoolean("target_present"))
                ),
                baseVersionId,
                limit,
                targetVersionId,
                limit,
                limit
        );
    }

    long countDistinctPaths(UUID baseVersionId, UUID targetVersionId) {
        Long count = jdbcTemplate.queryForObject(
                DISTINCT_PATH_COUNT_SQL,
                Long.class,
                baseVersionId,
                targetVersionId
        );
        return count == null ? 0 : count;
    }

    private static AssetSnapshot snapshot(ResultSet rs, String prefix, boolean present)
            throws SQLException {
        if (!present) {
            return null;
        }
        return new AssetSnapshot(
                rs.getString("path"),
                rs.getString(prefix + "mime_type"),
                rs.getLong(prefix + "size"),
                rs.getString(prefix + "sha256"),
                rs.getString(prefix + "role")
        );
    }
}
