package com.kbpack.pkg;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class VersionDiffDocumentRepository {

    record DocumentSnapshot(
            String sourcePath,
            String title,
            String docType,
            Integer orderNo,
            Integer wordCount,
            long contentLength,
            String contentHash
    ) {
    }

    record DocumentPair(
            String sourcePath,
            DocumentSnapshot base,
            DocumentSnapshot target
    ) {
    }

    private static final String DOCUMENT_PAIRS_SQL = """
            with base_docs as (
                select distinct on (source_path)
                       source_path, title, doc_type, order_no, word_count,
                       content_length, content_hash
                  from extracted_document
                 where version_id = ?
                 order by source_path, order_no, id
                 limit ?
            ), target_docs as (
                select distinct on (source_path)
                       source_path, title, doc_type, order_no, word_count,
                       content_length, content_hash
                  from extracted_document
                 where version_id = ?
                 order by source_path, order_no, id
                 limit ?
            )
            select coalesce(b.source_path, t.source_path) as source_path,
                   b.source_path is not null as base_present,
                   b.title as base_title,
                   b.doc_type as base_doc_type,
                   b.order_no as base_order_no,
                   b.word_count as base_word_count,
                   b.content_length as base_content_length,
                   b.content_hash as base_content_hash,
                   t.source_path is not null as target_present,
                   t.title as target_title,
                   t.doc_type as target_doc_type,
                   t.order_no as target_order_no,
                   t.word_count as target_word_count,
                   t.content_length as target_content_length,
                   t.content_hash as target_content_hash
              from base_docs b
              full outer join target_docs t on t.source_path = b.source_path
             order by source_path
             limit ?
            """;

    private static final String DISTINCT_PATH_COUNT_SQL = """
            select count(*)
              from (
                    select source_path from extracted_document where version_id = ?
                    union
                    select source_path from extracted_document where version_id = ?
                   ) documents
            """;

    private static final String CONTENT_PREVIEW_SQL = """
            select left(content, ?) as content
              from extracted_document
             where version_id = ? and source_path = ?
             order by order_no, id
             limit 1
            """;

    private final JdbcTemplate jdbcTemplate;

    VersionDiffDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<DocumentPair> loadDocumentPairs(UUID baseVersionId, UUID targetVersionId, int limit) {
        return jdbcTemplate.query(
                DOCUMENT_PAIRS_SQL,
                (rs, rowNum) -> new DocumentPair(
                        rs.getString("source_path"),
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

    Optional<String> loadContentPreview(UUID versionId, String sourcePath, int characterLimit) {
        List<String> contents = jdbcTemplate.query(
                CONTENT_PREVIEW_SQL,
                (rs, rowNum) -> rs.getString("content"),
                characterLimit,
                versionId,
                sourcePath
        );
        return contents.stream().findFirst();
    }

    private static DocumentSnapshot snapshot(ResultSet rs, String prefix, boolean present)
            throws SQLException {
        if (!present) {
            return null;
        }
        return new DocumentSnapshot(
                rs.getString("source_path"),
                rs.getString(prefix + "title"),
                rs.getString(prefix + "doc_type"),
                rs.getObject(prefix + "order_no", Integer.class),
                rs.getObject(prefix + "word_count", Integer.class),
                rs.getLong(prefix + "content_length"),
                rs.getString(prefix + "content_hash")
        );
    }
}
