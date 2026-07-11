package com.kbpack.common.page;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Unified pagination envelope (docs/00-canonical-spec.md §3.6).
 */
public record PageResponse<T>(
        long total,
        int page,
        @JsonProperty("page_size") int pageSize,
        List<T> items
) {
    public static <T> PageResponse<T> of(long total, int page, int pageSize, List<T> items) {
        return new PageResponse<>(total, page, pageSize, items);
    }
}
