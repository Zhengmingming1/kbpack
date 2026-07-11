package com.kbpack.parser;

import java.util.List;
import java.util.Map;

public record ParseResult(
        String detectedTitle,
        List<ParsedDocument> documents,
        Map<String, Object> qualityMeta
) {
    public ParseResult(String detectedTitle, List<ParsedDocument> documents) {
        this(detectedTitle, documents, null);
    }
}
