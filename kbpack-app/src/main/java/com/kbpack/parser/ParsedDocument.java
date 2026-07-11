package com.kbpack.parser;

import java.util.List;
import java.util.Map;

public record ParsedDocument(
        String sourcePath,
        String title,
        ExtractedDocument.DocType docType,
        int orderNo,
        String content,
        String rawContent,
        List<Map<String, Object>> headingTree
) {}
